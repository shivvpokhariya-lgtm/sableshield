# Hackathon submission

## Description (as submitted)
SableShield stops scam calls live, while you're still on the call. As the caller speaks, on-device AI flags scam tactics line by line — fake urgency ('blocked in 2 hours'), OTP demands, isolation ('don't tell anyone') — and delivers a verdict before money moves: SCAM DETECTED, HANG UP NOW. Safe calls pass too, with reasons.

Truecaller blocks numbers after the fact. SableShield analyses content during the call, so a first-time scammer on a fresh number is still caught.

Powered by Gemma 4 open weights: audio never leaves the phone — no recording, no storage, no server. Only open weights make live call protection legal. Open-source, MIT.

## Links
- Repo: https://github.com/shivvpokhariya-lgtm/sableshield
- Live demo: https://shivvpokhariya-lgtm.github.io/sableshield/

## Category rationale
- **Primary — Best Open-Source AI Project (DigitalOcean):** open weights are not a feature, they are the foundation. Remove them and the product cannot exist (privacy, speed, cost, offline all depend on them). Repo public, MIT.
- **Secondary — Best Use of Gemma 4:** live mode genuinely uses Gemma 4 (`gemma-4-26b-a4b-it`, live verdict verified). Text-only, so it is the supporting claim, not the headline.
