# Open source in SableShield

## What "open" means here, in two places
1. **The model is open — Gemma 4.** Its weights are public under Apache 2.0: downloadable from Hugging Face, Kaggle, and Google's developer portal, runnable on your own hardware, fine-tunable, commercial use allowed.
2. **The code is open — MIT.** This whole project is a public repo: website, demo, live mode, server. Anyone can read and run it.

## Where open-source AI is used
Live mode only. A pasted transcript goes to Gemma 4, which returns a structured verdict (verdict + score + flags + reasons). The scripted scam/safe demos do not call any model.

## Why open weights are necessary, not decorative
- **Privacy:** a closed model would force a live call to a server — the violation itself. Open weights run on-device, so the call never leaves the phone.
- **Speed:** a cloud round-trip (2–5s) is too slow; the victim shares the OTP meanwhile. On-device warns in milliseconds.
- **Cost:** per-call API fees make a free product impossible. Open weights cost zero per call.
- **Offline:** cloud AI dies in villages and on weak networks. On-device keeps working.

Remove open weights and the product stops existing. That is why it qualifies as open-source AI being the *important part*, not a side detail.

## Honest scope
- Demo transcripts are scripted (reliability on stage); live mode is the real AI call.
- The current build reaches Gemma via API for reliability; the production architecture runs the same open weights on-device (llama.cpp / Ollama / AI Edge). The API route does not make the model closed — openness is a property of the weights, not of the access route.
