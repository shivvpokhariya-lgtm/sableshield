# Glossary

- **Weights** — the billions of numbers inside a model. Training tunes them; they are the model's knowledge.
- **Open weights** — those numbers are public (Gemma 4, Apache 2.0): downloadable, runnable on your own hardware, fine-tunable.
- **Closed model** — weights never published; you only rent API access (GPT, Claude). It can never run on your device.
- **Gemma 4** — Google DeepMind's open model family. This project uses `gemma-4-26b-a4b-it` for live verdicts. Phone-sized siblings: E2B, E4B.
- **Verdict** — the final call on a transcript: SCAM or SAFE, with a 0–100 score.
- **Flag** — one detected scam tactic on a line (urgency, OTP request, isolation, impersonation, …).
- **Rule-based fallback** — deterministic pattern scoring used when no key is present. Not AI; a safety net so the product never fails.
- **STT (speech-to-text)** — converting spoken call audio into transcript text, on-device in the production architecture.
- **VoIP dialer** — a calling app that carries the call itself, so its audio is available to analyse.
- **OEM / carrier integration** — protection built into the phone's own dialer by the manufacturer or operator (Pixel Call Screen model).
- **MIT license** — this project's code license: anyone may use, copy, and modify it.
