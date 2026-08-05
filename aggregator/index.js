import express from 'express';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const UPSTREAMS = [
  {
    name: 'linjian',
    url: process.env.LINJIAN_MCP_URL,
    token: process.env.LINJIAN_TOKEN,
  },
  {
    name: 'galatea',
    url: process.env.GALATEA_MCP_URL,
  },
];

// 缓存：每个上游的 client + 工具列表，带过期时间
const cache = new Map(); // name -> { client, tools, expiresAt }
const CACHE_TTL = 5 * 60 * 1000; // 5分钟缓存，避免每次请求都重连

async function connectUpstream(upstream) {
  const headers = upstream.token
    ? { Authorization: `Bearer ${upstream.token}` }
    : {};
  const client = new Client({ name: 'aggregator', version: '1.0.0' });
  const transport = new StreamableHTTPClientTransport(
    new URL(upstream.url),
    { requestInit: { headers } }
  );
  await client.connect(transport);
  const result = await client.listTools();
  return { client, tools: result.tools };
}

// 获取（或重新建立）某个上游的连接，带重试
async function getUpstream(upstream, retries = 3) {
  const cached = cache.get(upstream.name);
  if (cached && cached.expiresAt > Date.now()) {
    return cached;
  }
  let lastErr;
  for (let i = 0; i < retries; i++) {
    try {
      const { client, tools } = await connectUpstream(upstream);
      const entry = { client, tools, expiresAt: Date.now() + CACHE_TTL };
      cache.set(upstream.name, entry);
      console.log(`[${upstream.name}] 连接成功，${tools.length} 个工具`);
      return entry;
    } catch (err) {
      lastErr = err;
      console.error(`[${upstream.name}] 连接失败(第${i + 1}次): ${err.message}`);
      // Render 免费实例冷启动可能要等 30-50 秒，重试前等一下
      await new Promise((r) => setTimeout(r, 8000));
    }
  }
  throw lastErr;
}

// 获取全部工具（并行拉取所有上游，单个失败不影响其他）
async function getAllTools() {
  const allTools = [];
  const toolOwner = new Map(); // toolName -> upstream name

  const results = await Promise.allSettled(
    UPSTREAMS.filter((u) => u.url).map((u) => getUpstream(u).then((e) => ({ u, e })))
  );

  for (const r of results) {
    if (r.status === 'fulfilled') {
      const { u, e } = r.value;
      for (const tool of e.tools) {
        allTools.push(tool);
        toolOwner.set(tool.name, u.name);
      }
    } else {
      console.error('上游拉取失败:', r.reason?.message);
    }
  }
  return { allTools, toolOwner };
}

async function callTool(name, args) {
  // 先在现有 cache 里找
  let owner = UPSTREAMS.find((u) => {
    const cached = cache.get(u.name);
    return cached && cached.tools.some((t) => t.name === name);
  });

  // cache 里没有的话，先刷新一次再找
  if (!owner) {
    console.log(`[callTool] cache 未命中 "${name}"，重新拉取所有工具...`);
    await getAllTools();
    owner = UPSTREAMS.find((u) => {
      const cached = cache.get(u.name);
      return cached && cached.tools.some((t) => t.name === name);
    });
  }

  if (!owner) throw new Error(`未知工具: ${name}`);
  const entry = await getUpstream(owner);
  return await entry.client.callTool({ name, arguments: args });
}

async function main() {
  const app = express();
  app.use(express.json());
  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, DELETE');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, mcp-session-id');
    if (req.method === 'OPTIONS') return res.sendStatus(200);
    next();
  });

  app.get('/health', async (_req, res) => {
    // 访问 /health 时顺带唤醒并刷新所有上游
    const { allTools } = await getAllTools();
    res.json({ ok: true, tools: allTools.length });
  });

  app.delete('/mcp', (_req, res) => res.sendStatus(200));
  app.get('/mcp', (_req, res) => res.sendStatus(405));

  app.post('/mcp', async (req, res) => {
    const { method, params, id } = req.body;
    const ok = (result) => res.json({ jsonrpc: '2.0', id, result });
    const err = (code, message) => res.json({ jsonrpc: '2.0', id, error: { code, message } });

    try {
      if (method === 'initialize') {
        return ok({
          protocolVersion: '2024-11-05',
          capabilities: { tools: {} },
          serverInfo: { name: 'mcp-aggregator', version: '1.0.0' },
        });
      }
      if (method === 'notifications/initialized') return res.sendStatus(202);
      if (method === 'ping') return ok({});

      if (method === 'tools/list') {
        const { allTools } = await getAllTools();
        return ok({ tools: allTools });
      }

      if (method === 'tools/call') {
        const { name, arguments: args } = params;
        const result = await callTool(name, args);
        return ok(result);
      }

      return err(-32601, `Method not found: ${method}`);
    } catch (e) {
      return err(-32603, e.message);
    }
  });

  const port = process.env.PORT || 3000;
  app.listen(port, () => console.log(`MCP Aggregator on :${port}`));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
