// SableShield backend — zero-dependency Node.js server (Node 18+).
// Serves the frontend and proxies scam-call analysis to Gemma 4.
//
// Why a backend exists at all:
//   - The Gemini API key must NEVER ship to the browser. It lives here,
//     in an environment variable (GEMINI_API_KEY), server-side only.
//   - The same analysis endpoint works in three modes:
//       1. Gemma live (key present) — real model verdict
//       2. Rule-based (no key) — instant, deterministic, zero fail
//       3. Demo scripts — served by the frontend for judging
//
// Run:
//   GEMINI_API_KEY=your_key node server.js
//   → open http://localhost:3000
// Without a key the server still runs fully (rule-based mode).

const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = process.env.PORT || 3000;
const ROOT = __dirname;

// --- Community scam reports (in-memory, demo-grade) ---
// Reports reset when the server restarts. Deliberate for the hackathon:
// no database, no PII store — just a shared warning list. Numbers are
// masked before storage so a report can warn others without doxxing.
const reports = [];
function maskNumber(n) {
  const s = String(n || '').replace(/\s/g, '');
  if (s.length < 7) return s;
  return s.slice(0, 4) + 'XXXX' + s.slice(-2);
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
};

// --- Rule-based analyser (mirrors app.js, server-side) ---
function ruleBased(text) {
  const t = (text || '').toLowerCase();
  const flags = [];
  let score = 5;
  if (/otp|one time password|verification code/.test(t)) { flags.push('An OTP is being requested — a real bank or company never asks for an OTP on a call'); score += 40; }
  if (/block|blocked|freeze|suspend|close.*account|urgent|immediately|within.*hour|2 hours|today itself/.test(t)) { flags.push('Urgency / threat — “account blocked” used to rush you'); score += 25; }
  if (/do not tell|don.t tell|secret|confidential|don.t inform/.test(t)) { flags.push('“Do not tell anyone” — isolation tactic'); score += 20; }
  if (/password|pin|card number|cvv|bank detail|account number/.test(t)) { flags.push('Password, PIN, or card details are being requested'); score += 30; }
  if (/prize|won|lottery|gift|offer.*expire|click.*link|download.*app|anydesk|teamviewer/.test(t)) { flags.push('Prize, link, or remote-access app — common scam pattern'); score += 25; }
  if (/police|arrest|legal|case|fine/.test(t)) { flags.push('Police / legal threat — fear tactic'); score += 20; }
  score = Math.min(98, score);
  if (flags.length === 0) {
    return { type: 'safe', title: '✅ NO SCAM SIGNALS FOUND', score: 'LOW RISK', sub: 'No classic scam patterns found in this text. Still: never share an OTP or password on a call.', safe: ['No OTP or password demand', 'No urgency or threat', 'No isolation pressure'], mode: 'rule-based' };
  }
  return { type: 'scam', title: '⚠️ SCAM SIGNALS FOUND — BE CAREFUL', score: score + '% SCAM RISK', sub: flags.length + ' red flag(s) found. If the caller is asking for an OTP or password, hang up — this is almost certainly a scam.', flags, mode: 'rule-based' };
}

// --- Gemma 4 live analysis (server-side key, never exposed) ---
async function gemmaAnalyse(text) {
  const key = process.env.GEMINI_API_KEY;
  if (!key) return null;
  const prompt = `You are SableShield, a scam-call detector. Analyse this caller transcript and return ONLY valid JSON (no markdown, no fences):\n{\n  "type": "scam" or "safe",\n  "score": "e.g. 92% SCAM RISK or LOW RISK",\n  "title": "short verdict",\n  "sub": "one sentence explanation",\n  "flags": ["red flag 1", "red flag 2"],\n  "safe": ["safe reason 1"]\n}\nTranscript: ${text}`;
  const url = 'https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent?key=' + encodeURIComponent(key);
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }], generationConfig: { temperature: 0.2, maxOutputTokens: 800 } }),
  });
  if (!res.ok) throw new Error('Gemma API ' + res.status);
  const data = await res.json();
  let out = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
  out = out.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim();
  const parsed = JSON.parse(out);
  parsed.mode = 'gemma-4-live';
  return parsed;
}

function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}

function serveFile(res, filePath) {
  const ext = path.extname(filePath).toLowerCase();
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); res.end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const pathname = url.pathname;

  // Health check
  if (pathname === '/api/health') {
    return sendJson(res, 200, { ok: true, gemma: Boolean(process.env.GEMINI_API_KEY), mode: process.env.GEMINI_API_KEY ? 'gemma-4-live' : 'rule-based' });
  }

  // Analysis endpoint
  if (pathname === '/api/analyze' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; if (body.length > 20000) req.destroy(); });
    req.on('end', async () => {
      try {
        const { text } = JSON.parse(body || '{}');
        if (!text || typeof text !== 'string' || !text.trim()) {
          return sendJson(res, 400, { error: 'Provide { text } — the caller transcript.' });
        }
        try {
          const live = await gemmaAnalyse(text);
          if (live) return sendJson(res, 200, live);
        } catch (e) {
          // Fall through to rule-based on any Gemma failure — demo never dies.
        }
        return sendJson(res, 200, ruleBased(text));
      } catch (e) {
        return sendJson(res, 400, { error: 'Invalid JSON body.' });
      }
    });
    return;
  }

  // Community scam reports — submit a scam number + verdict
  if (pathname === '/api/report' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; if (body.length > 20000) req.destroy(); });
    req.on('end', () => {
      try {
        const data = JSON.parse(body || '{}');
        const entry = {
          id: reports.length + 1,
          caller: String(data.caller || 'Unknown').slice(0, 120),
          number: maskNumber(data.number || ''),
          verdict: String(data.verdict || 'scam').slice(0, 20),
          score: String(data.score || '').slice(0, 40),
          flags: Array.isArray(data.flags) ? data.flags.map(f => String(f).slice(0, 200)).slice(0, 10) : [],
          at: new Date().toISOString(),
        };
        reports.push(entry);
        if (reports.length > 100) reports.shift();
        return sendJson(res, 200, { ok: true, total: reports.length, id: entry.id });
      } catch (e) {
        return sendJson(res, 400, { error: 'Invalid JSON body.' });
      }
    });
    return;
  }

  // Community scam reports — list (latest first)
  if (pathname === '/api/reports' && req.method === 'GET') {
    return sendJson(res, 200, { total: reports.length, reports: reports.slice(-20).reverse() });
  }

  // Static files (default: index.html)
  let filePath = pathname === '/' ? path.join(ROOT, 'index.html') : path.join(ROOT, pathname);
  // Block path traversal
  if (!filePath.startsWith(ROOT)) { res.writeHead(403); res.end('Forbidden'); return; }
  // Block sensitive files from being served
  const base = path.basename(filePath);
  if (base === 'server.js' || base === 'package.json' || base.startsWith('.env')) {
    res.writeHead(403); res.end('Forbidden'); return;
  }
  serveFile(res, filePath);
});

server.listen(PORT, () => {
  console.log(`SableShield running at http://localhost:${PORT}`);
  console.log(`Mode: ${process.env.GEMINI_API_KEY ? 'Gemma 4 live (key present)' : 'Rule-based (no key — set GEMINI_API_KEY for live Gemma)'}`);
});
