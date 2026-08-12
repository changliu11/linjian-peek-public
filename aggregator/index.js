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
    token: process.env.GALATEA_MCP_TOKEN,
  },
];

const cache = new Map();
const CACHE_TTL = 5 * 60 * 1000;

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
      await new Promise((r) => setTimeout(r, 8000));
    }
  }
  throw lastErr;
}

async function getAllTools() {
  const allTools = [];
  const results = await Promise.allSettled(
    UPSTREAMS.filter((u) => u.url).map((u) => getUpstream(u).then((e) => ({ u, e })))
  );
  for (const r of results) {
    if (r.status === 'fulfilled') {
      for (const tool of r.value.e.tools) {
        allTools.push(tool);
      }
    } else {
      console.error('上游拉取失败:', r.reason?.message);
    }
  }
  return allTools;
}

async function callTool(name, args) {
  // 找持有这个工具的上游
  const findOwner = () => UPSTREAMS.find((u) => {
    const cached = cache.get(u.name);
    return cached && cached.tools.some((t) => t.name === name);
  });

  let owner = findOwner();
  if (!owner) {
    // cache 里没有，刷新一次
    await getAllTools();
    owner = findOwner();
  }
  if (!owner) throw new Error(`未知工具: ${name}`);

  // 第一次调用
  try {
    const entry = await getUpstream(owner);
    return await entry.client.callTool({ name, arguments: args });
  } catch (err) {
    // 调用失败说明连接断了，清除缓存强制重连再试一次
    console.error(`[${owner.name}] 工具调用失败，清除缓存重连: ${err.message}`);
    cache.delete(owner.name);
    const entry = await getUpstream(owner);
    return await entry.client.callTool({ name, arguments: args });
  }
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
    const allTools = await getAllTools();
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
        const allTools = await getAllTools();
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

main().catch((err) => { console.error(err); process.exit(1); });
