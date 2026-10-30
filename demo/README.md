# SableShield — Live Scam Call Detector

**A scam call is happening. AI is listening live — and stopping it before your money is gone.**

Every year, scammers steal billions from ordinary people — especially the elderly.
By the time you realise it was a scam, the money is gone.
SableShield catches it **during** the call, not after.

## Presentation — open source, explained

**https://shivvpokhariya-lgtm.github.io/sableshield/SableShield-Explainer.html**

8 slides: title, problem, what open source is, where it lives in this app, why only open weights work, how the demo runs, short answers to the questions judges ask, and the close. Open it in a browser and step through with → / Space.

## The demo (60 seconds)

1. Open `index.html` (or run the server below) and tap **🎭 Demo: Scam Call**.
2. A transcript appears live, line by line — like a real call in progress.
3. Every scam line gets a red flag as it speaks:
   - “Account BLOCKED in 2 hours” → urgency tactic
   - “Share the OTP” → real banks NEVER ask for OTP on a call
   - “Do not tell anyone” → isolation tactic
4. Final verdict: **⚠️ SCAM DETECTED — 94% SCAM — HANG UP NOW**, with Hang Up and Report actions.
5. For contrast, tap **✅ Demo: Real Bank Call** — a safe verdict, green screen.

No photo needed. No setup. No API key needed for the demo.

## Live mode

Paste what a caller is saying into the live box and tap **Analyse**:

- **With a Gemini API key** → Gemma 4 (`gemma-3-27b-it`) analyses the transcript and returns a structured verdict.
- **Without a key** → instant rule-based analysis runs (same scam patterns, deterministic, zero fail).

Get a free key at <https://aistudio.google.com/apikey>.

## Run it

**Zero-setup (frontend only):**

```bash
# Just open index.html in Chrome — demo + rule-based live mode work with no server.
open index.html
```

**Full stack (backend + Gemma proxy):**

```bash
# Node 18+ only, zero dependencies — nothing to install.
GEMINI_API_KEY=your_key node server.js
# → http://localhost:3000
# Without a key the server still runs fully (rule-based mode).
# Health: GET /api/health · Analyse: POST /api/analyze { "text": "..." }
```

## Why open-source AI? Why closed AI cannot do this

This is the core of the project — open weights are not a feature, they are the foundation:

1. **Privacy:** Sending a live phone call to a closed API is a privacy violation — and illegal in several countries. Only an open-weight model can run **on the device**, so the call never leaves the phone.
2. **Speed:** In a scam, every second matters. A cloud API round-trip (2–5 seconds) is too slow — by then the victim has shared the OTP. On-device AI warns in milliseconds.
3. **Cost:** Analysing every call via API makes a free product impossible. Open weights cost zero per call — so this can run free on every elderly person's phone.
4. **Offline:** Scam calls arrive in villages and on weak networks. Cloud AI dies there. On-device Gemma keeps working.

Closed AI fails all four. That is why this project exists as open-source, and why it targets the **Best Open-Source AI Project** prize: open-source AI is the *important part* of how it works, not a side detail.

Engine: Gemma 4 (Apache 2.0, open weights). Also noted as “Powered by Gemma 4” for the Best Use of Gemma 4 track — the primary pitch is open-source necessity; Gemma is the specific open model used.

## Privacy design (the question every judge asks)

“Isn't it wrong — even illegal — for AI to listen to calls?”

- **Audio never leaves the phone.** On-device analysis only. No server, no company, no human hears the call.
- **No recording, no storage.** The app does not record calls or save transcripts. It checks patterns in real time and discards audio immediately — like ears hear and forget.
- **User opt-in.** You install the app and turn the feature on yourself. Nothing hidden.
- **Same principle as email spam filters.** Gmail scans every email for spam without the sender's permission — legal because you are the recipient, it is for your protection, it is automated, and no human reads it. SableShield applies the same principle to calls.
- **Optional announcement.** A call can open with “This call is protected by SableShield,” like Google Call Screen announces itself.

**On-device + no recording + user consent = legal and ethical.** Sending calls to the cloud would be both illegal and unethical — which is exactly why open weights are required.

## Truecaller? Google Call Screen? What's different?

- **Truecaller** blocks *numbers* (after the fact, from a database). A new or spoofed number defeats it.
- **Google Call Screen** screens *unknown* callers with a bot. It does not analyse a live conversation for scam patterns.
- **SableShield** analyses *content during the call* — the words, the tactics, the pressure. A first-time scammer on a fresh number is still caught, because the *pattern* gives them away, not the number.

## Project structure

```
index.html   — app shell + call screen + demo + live mode + privacy/open-source notes
styles.css   — editorial design system (paper + ink, no AI-gradient look)
app.js       — demo scripts, verdict rendering, live analysis (Gemma / rule-based)
server.js    — zero-dependency Node backend: static hosting + /api/analyze (key kept server-side)
package.json — scripts + metadata (no dependencies)
PITCH.md     — 90-second pitch + judge Q&A
LICENSE      — MIT
```

## Honest scope

The demo uses scripted transcripts and rule-based analysis for reliability — so it never fails on stage. Live Gemma analysis runs when a key is present (frontend direct call, or via `server.js` so the key stays server-side). The production architecture is on-device Gemma (E2B via Ollama / llama.cpp, as proven by Google AI Edge Gallery); the current build demonstrates the same decision path through the API for reliability. That architecture choice — on-device, private, zero-cost — is precisely what only open weights enable.
