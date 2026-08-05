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

const toolMap = new Map();
const allTools = [];

async function initUpstreams() {
  for (const upstream of UPSTREAMS) {
    if (!upstream.url) continue;
    try {
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
      for (const tool of result.tools) {
        toolMap.set(tool.name, { client });
        allTools.push(tool);
      }
      console.log(`[${upstream.name}] 连接成功，${result.tools.length} 个工具`);
    } catch (err) {
      console.error(`[${upstream.name}] 连接失败:`, err.message);
    }
  }
  console.log(`共 ${allTools.length} 个工具`);
}

async function main() {
  await initUpstreams();

  const app = express();
  app.use(express.json());
  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, DELETE');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, mcp-session-id');
    if (req.method === 'OPTIONS') return res.sendStatus(200);
    next();
  });

  app.get('/health', (_req, res) => res.json({ ok: true, tools: allTools.length }));
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

      if (method === 'tools/list') return ok({ tools: allTools });

      if (method === 'tools/call') {
        const { name, arguments: args } = params;
        const entry = toolMap.get(name);
        if (!entry) return err(-32601, `Tool not found: ${name}`);
        const result = await entry.client.callTool({ name, arguments: args });
        return ok(result);
      }

      return err(-32601, `Method not found: ${method}`);
    } catch (e) {
      return err(-32603, e.message);
    }
  });

  const port = process.env.PORT || 3000;
  app.listen(port, () => console.log(`MCP Aggregator on :${port}, ${allTools.length} tools`));
}

main().catch(err => { console.error(err); process.exit(1); });
