# Contributing to SableShield

## Run it
- Frontend only: open `index.html` in Chrome. Demo + rule-based live mode work with no server and no key.
- Full stack: `GEMINI_API_KEY=your_key node server.js` → http://localhost:3000 (without a key the server still runs in rule-based mode).

## What to work on
- New demo transcripts (scam + safe) in `examples/` — realistic, short, with clear scam signals.
- Live-mode parsing: the Gemma verdict JSON must survive markdown fences and extra prose.
- Docs: anything a judge or a new contributor would trip over.

## Rules
- Never commit a real API key, `.env`, or any secret. Keys live in browser localStorage or server env only.
- No build step: vanilla HTML/CSS/JS. Keep `index.html` + `app.js` working by just opening the file.
- One logical change per commit, with a clear message.
- Test without a key first: if it does not work keyless (rule-based path), it is not done.
