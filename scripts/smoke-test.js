// Smoke test for the SableShield backend (no API key needed).
// Usage: SERVER_PATH=../server.js node scripts/smoke-test.js
// Spawns server.js, checks /api/health, /api/analyze (rule-based), /api/report, then exits 0 on PASS.
const { spawn } = require('child_process');
const http = require('http');
const path = require('path');

const serverPath = process.env.SERVER_PATH || path.join(__dirname, '..', 'server.js');
const PORTS = [Number(process.env.SMOKE_PORT) || 3098, 3000];

function req(port, method, p, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const r = http.request({ host: '127.0.0.1', port, path: p, method,
      headers: data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {} },
      (res) => { let b = ''; res.on('data', (c) => (b += c)); res.on('end', () => { try { resolve(JSON.parse(b)); } catch { resolve(b); } }); });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const child = spawn(process.execPath, [serverPath],
    { env: { ...process.env, PORT: String(PORTS[0]) }, stdio: 'ignore' });
  let port = null;
  let health = null;
  try {
    for (let i = 0; i < 20 && !health; i++) {
      for (const p of PORTS) {
        try { health = await req(p, 'GET', '/api/health'); port = p; break; } catch { /* try next port */ }
      }
      if (!health) await sleep(500);
    }
    if (!health) throw new Error('server did not become healthy');
    console.log('health:', JSON.stringify(health));
    const verdict = await req(port, 'POST', '/api/analyze', { text: 'Hello, I am calling from your bank. Your account will be blocked in 2 hours unless you share the OTP. Do not tell anyone.' });
    console.log('analyze:', JSON.stringify(verdict));
    if (!JSON.stringify(verdict).toUpperCase().includes('SCAM')) throw new Error('analyze did not flag the scam line');
    const report = await req(port, 'POST', '/api/report', { caller: 'Unknown', number: '0000000000', verdict: 'SCAM', score: 94, flags: ['OTP', 'Urgency'] });
    console.log('report:', JSON.stringify(report));
    console.log('PASS');
    process.exitCode = 0;
  } catch (e) {
    console.error('FAIL:', e.message);
    process.exitCode = 1;
  } finally {
    child.kill();
    setTimeout(() => process.exit(process.exitCode || 0), 500);
  }
})();
