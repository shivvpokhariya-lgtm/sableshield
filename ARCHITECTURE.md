# Architecture

## Pieces
- `index.html` — the whole page: hero, starfield canvas, demo section, live-mode card, footer.
- `app.js` — all browser logic: starfield, logo/nav animation, scripted demos, verdict rendering, live analysis (Gemma / rule-based), key storage in localStorage.
- `server.js` — optional zero-dependency Node backend: static hosting + `/api/health`, `/api/analyze` (key stays server-side), `/api/report`, `/api/reports` (in-memory).

## Verdict flow
1. Transcript in (scripted demo lines, or user-pasted text in live mode).
2. Engine out: Gemma 4 (`gemma-4-26b-a4b-it`) returns verdict + score + flags + reasons; without a key, the deterministic rule-based analyzer scores the same patterns (OTP +40, urgency +25, isolation +20, card/CVV +30, prize/link/remote-access +25, police/legal +20, capped at 98).
3. Verdict card renders: burgundy SCAM card or olive SAFE card, with flags and Hang Up / Report actions.

## Why open weights (the whole point)
Privacy needs on-device analysis; on-device needs open weights. A closed model would force live calls to a server — the violation itself. See `docs/open-source.md`.

## From prototype to live calls
The demo proves the detection engine (transcript → pattern → verdict). A real call only changes the transcript source (scripted lines → on-device speech-to-text). Paths, in order: speakerphone + mic MVP → VoIP dialer app → carrier/OEM integration. See `docs/live-call-roadmap.md`.
