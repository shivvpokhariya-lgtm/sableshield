# Judge Q&A crib

## Where is the open-source AI used?
In live mode only. Gemma 4 (open weights) analyses a pasted transcript and returns verdict + score + flags + reasons. The scripted demos are not AI — they exist for on-stage reliability. The code is also open: public repo, MIT license.

## Why not GPT?
Closed. A live call would go to its server (the violation itself), it costs per call, it is slow (2–5s round-trip), and it never works offline.

## How is this different from Truecaller?
Truecaller blocks numbers, after the fact, from a database. SableShield reads content during the call — a first-time scammer on a fresh number is still caught by the pattern.

## When will it work on a real call?
No third-party app can tap cellular call audio (Android exposes numbers, not audio; iOS exposes nothing). Production paths, in order: speakerphone + mic MVP → VoIP dialer app → carrier/OEM integration (Pixel Call Screen model). The demo proves the detection engine; a live call only changes the transcript source.

## What happens to my key?
Saved only in your own browser localStorage, sent only to Google's API for that analysis. With the backend running, it lives only in server env. Never in the repo.

## What if there is no key?
The rule-based fallback runs (OTP +40, urgency +25, isolation +20, card/CVV +30, prize/link +25, police/legal +20, capped at 98). Same patterns, no AI. The product never fails.

## What are weights?
The billions of numbers inside a model. Training tunes them; they are the model's knowledge. Open weights = those numbers are public (Gemma 4, Apache 2.0) — downloadable, runnable on your own hardware. Closed models never publish them.
