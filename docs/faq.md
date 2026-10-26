# FAQ

## Where is Gemma used on the website?
Live mode only ("Live mode — try it yourself" card). The scripted scam/safe demos never call any model.

## What happens when I enter an API key?
It is saved in your own browser localStorage and auto-fills next time. On Analyse, the app tries the backend first (if `server.js` is running), then a direct Gemma call with the key, then the rule-based fallback. The key is sent only to Google's API, never stored on any server of ours, never in the repo.

## How does it run offline?
Gemma's weights are public, so a copy of the model can live on the device. Download once (online), then a local runtime (llama.cpp / Ollama / AI Edge) runs it with zero internet at call time. Phone-sized variants (E2B/E4B) or quantized builds fit phone hardware.

## How will it work on a real call?
See `docs/live-call-roadmap.md`: speakerphone MVP → VoIP dialer → carrier/OEM. The demo proves the engine; a live call changes only the transcript source.

## What if there is no key?
The rule-based fallback scores the same patterns deterministically (OTP +40, urgency +25, isolation +20, card/CVV +30, prize/link +25, police/legal +20, capped at 98). Zero flags → LOW RISK.

## Where is the source?
- Working copy: `~/Documents/ScamShield/` on the author's Mac.
- Public repo: https://github.com/shivvpokhariya-lgtm/sableshield
- Live site: https://shivvpokhariya-lgtm.github.io/sableshield/

## How do I run it?
Frontend only: open `index.html` in Chrome. Full stack: `GEMINI_API_KEY=your_key node server.js` → http://localhost:3000.
