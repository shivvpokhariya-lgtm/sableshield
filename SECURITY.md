# Security and privacy policy

## What we collect: nothing
- No accounts, no analytics, no tracking.
- Call transcripts are never recorded or stored. A verdict is computed and discarded.
- The demo uses scripted transcripts; live mode analyses only the text the user pastes.

## Keys
- A pasted Gemini key is saved only in the user's own browser localStorage (`sableshield_key`) and is sent only to Google's API for that analysis.
- With the backend running, the key lives only in server env (`GEMINI_API_KEY`) and never reaches the browser.
- Never commit a key: `.env` is git-ignored and `.env.example` is the only template.

## Dependencies
- Backend: zero dependencies (Node 18+ built-ins only).
- Frontend: Tailwind CDN + Google Fonts at demo time; no data leaves the page except the optional Gemma call.

## Reporting an issue
Open a GitHub issue describing the problem without including any key, token, or personal data.
