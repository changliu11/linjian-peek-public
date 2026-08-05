import express from 'express';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { ListToolsRequestSchema, CallToolRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { randomUUID } from 'crypto';

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
    if (!upstream.url) {
      console.warn(`[${upstream.name}] URL not set, skipping`);
      continue;
    }
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
      console.log(`[${upstream.name}] 已连接，加载了 ${result.tools.length} 个工具`);
    } catch (err) {
      console.error(`[${upstream.name}] 连接失败:`, err.message);
    }
  }
  console.log(`共加载 ${allTools.length} 个工具`);
}

async function main() {
  await initUpstreams();

  const app = express();
  app.use(express.json());

  // CORS
  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, DELETE');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, mcp-session-id');
    if (req.method === 'OPTIONS') return res.sendStatus(200);
    next();
  });

  // OAuth metadata
  app.get('/.well-known/oauth-authorization-server', (req, res) => {
    const base = `${req.protocol}://${req.hostname}`;
    res.json({
      issuer: base,
      authorization_endpoint: `${base}/oauth/authorize`,
      token_endpoint: `${base}/oauth/token`,
      response_types_supported: ['code'],
      grant_types_supported: ['authorization_code'],
      code_challenge_methods_supported: ['S256'],
    });
  });

  app.get('/health', (_req, res) => {
    res.json({ ok: true, tools: allTools.length });
  });

  app.get('/mcp', (_req, res) => {
    res.status(405).json({ error: 'Use POST for MCP' });
  });

  app.post('/mcp', async (req, res) => {
    const server = new Server(
      { name: 'mcp-aggregator', version: '1.0.0' },
      { capabilities: { tools: {} } }
    );

    server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: allTools }));

    server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;
      const entry = toolMap.get(name);
      if (!entry) throw new Error(`未知工具: ${name}`);
      return await entry.client.callTool({ name, arguments: args });
    });

    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => randomUUID(),
    });

    res.on('close', () => transport.close());
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  });

  app.delete('/mcp', (_req, res) => {
    res.status(200).json({ ok: true });
  });

  const port = process.env.PORT || 3000;
  app.listen(port, () =>
    console.log(`MCP Aggregator 运行在 :${port}，共 ${allTools.length} 个工具`)
  );
}

main().catch((err) => {
  console.error('启动失败:', err);
  process.exit(1);
});
