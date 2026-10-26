# SableShield — 90-Second Pitch + Judge Q&A

## The pitch — 2 MINUTES, Altman/Musk style (say it plain, pause, demo-led)

Rules: very few words, short sentences, long pauses, show don't tell, stop at the end — never summarise.

**0:00–0:15 — Open:**
> “Hi, I’m Shivansh. This is SableShield. It stops scam calls — live, while you’re still on the call.”
>
> [pause — 2 seconds]

**0:15–0:45 — Problem (first principles):**
> “Last year, scammers stole billions from ordinary people — mostly elderly parents. One call. ‘I’m from your bank, share the OTP.’ And life savings are gone.”
>
> [pause]
>
> “The fundamental problem: trust is exploited in real time — but every protection we have acts after the fact. Block the number later. Report it later. The money is already gone.”

**0:45–1:25 — Demo (show, don’t tell):**
> “Watch.” [play the Scam Call demo — say nothing while it plays, point at each flag]
>
> “Lines appear as spoken. Flags as they speak. Verdict before the money moves.”
>
> [pause]
>
> “And the contrast — a real bank call. No OTP, no urgency, official number. It passes. That’s the whole product.”

**1:25–1:50 — Why open weights (the prize line, slowly):**
> “This runs on Gemma 4 — open weights. That is not a detail. It is the whole point.”
>
> [pause]
>
> “Your live phone call can never go to a cloud server — that would be a privacy violation. Only an open-weight model runs on your phone, analyses in milliseconds, costs zero, and works offline. Closed AI fails all four.”

**1:50–2:00 — Close (one line, then stop):**
> “AI listens live — and stops the scam before your money is gone.”
>
> [stop. “Thank you.” Nothing more.]

## Judge Q&A (pre-answered)

**“Truecaller already does this, no?”**
> “Truecaller blocks numbers from a database — after the fact. A new or spoofed number defeats it. SableShield analyses content during the call — the words, the tactics, the pressure. A first-time scammer on a fresh number is still caught, because the pattern gives them away, not the number.”

**“GPT can analyse a transcript too.”**
> “Paste a transcript into GPT after the call and it will analyse it — but by then the money is gone. SableShield works live, on-device, in milliseconds. And sending your live call to OpenAI is itself a privacy violation. That’s why this has to be open-weight, on-device.”

**“Isn’t it illegal for AI to listen to calls?”**
> “Great question — we designed for it. Audio never leaves the phone, nothing is recorded or stored, analysis is real-time and discarded immediately, and the user opts in. It’s the same principle as Gmail’s spam filter, which scans your emails without the sender’s permission for your protection. And here’s the key: only open weights make that possible. A closed API would have to send your call to a server — that WOULD be a privacy violation.”

**“Does it actually run on-device in this demo?”**
> “Honest answer: the hackathon demo uses scripted transcripts and API/rule-based analysis for stage reliability — so it never fails live. The production architecture is on-device Gemma E2B via Ollama / llama.cpp, exactly as Google AI Edge Gallery proves is possible. The decision path demonstrated here — pattern → verdict → action — is identical. And the reason we can even claim on-device is open weights; closed models can’t be deployed that way at all.”

**“What about non-English scam calls?”**
> “Gemma 4 is multilingual, and the rule patterns extend per language. Scam tactics — urgency, fear, isolation, OTP requests — are the same in every language; only the words change. That’s why this scales globally: same engine, localised patterns.”

**“A webpage can't listen to my real calls — so how does this attach to a live call?”**
> “You're right, and we never claimed otherwise — the demo proves the detection engine, not the plumbing. A third-party app can't tap a cellular call: Android's screening API sees numbers, not audio, and iOS gives third parties no call audio at all. Production has three honest paths: a VoIP dialer, where SableShield itself carries the call so the audio is ours to analyse on-device; carrier or OEM integration, the Pixel Call Screen model; or, as a last resort, speakerphone-plus-mic, which is fragile. The engine is the new part — the plumbing is a partnership — and that's exactly why on-device open weights matter: whoever carries the call, the audio must never leave the phone.”

## Demo order (do not improvise)

1. Scam Call demo (the dramatic one) — let the transcript play, point at flags as they appear.
2. Verdict + Hang Up + Report buttons.
3. Real Bank Call demo (contrast, 20 seconds).
4. If asked: live mode — paste a new scam line, tap Analyse, show verdict.
5. If asked about privacy: answer from the README's privacy section — on-device, no recording, no storage, user opt-in.
