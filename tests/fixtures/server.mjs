// Exclusively for controlled tests. This service is absent from the standard Compose stack.
import http from 'node:http';
import { randomUUID } from 'node:crypto';
const audits = new Map();
const server = http.createServer(async (request, response) => {
  const path = new URL(request.url, 'http://fixture').pathname;
  const reply = (code, body) => { response.writeHead(code, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(body)); };
  if (path.startsWith('/status/')) return reply(Number(path.split('/')[2]) || 500, { source: 'controlled-test-endpoint' });
  if (path === '/slow') return setTimeout(() => reply(200, { response: 'after-delay' }), 2500);
  if (path.startsWith('/api/audits')) {
    if (request.headers.authorization !== 'Bearer test-fixture-token') return reply(401, { message: 'Invalid fixture credential' });
    if (request.method === 'POST') {
      let bytes = ''; for await (const chunk of request) bytes += chunk;
      const body = JSON.parse(bytes || '{}');
      if (!body.authorizationConfirmed || !body.url) return reply(400, { message: 'Authorization and URL required' });
      const id = randomUUID(); audits.set(id, { id, status: 'COMPLETED', overallScore: 87, url: body.url });
      return reply(201, { id, status: 'PENDING' });
    }
    const audit = audits.get(path.split('/').pop()); return reply(audit ? 200 : 404, audit ?? { message: 'Not found' });
  }
  if (path === '/webhook/pulseops' && request.method === 'POST') return reply(202, { accepted: true });
  reply(200, { status: 'UP' });
});
server.listen(8080, '0.0.0.0');
