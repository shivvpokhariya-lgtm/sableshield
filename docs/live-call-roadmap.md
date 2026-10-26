# Live-call roadmap: from prototype to a real call

## The pipeline (same for all three paths)
Call audio → on-device speech-to-text (streaming transcript) → on-device Gemma (open weights) → pattern/score → verdict → mid-call alert (banner, "this looks like a scam", Hang Up suggestion). Every step must stay on-device for privacy.

## Why the webpage cannot attach to a call today
Cellular call audio is isolated by the OS. Android's screening API sees numbers, not audio. iOS gives third parties no call audio at all. A browser page has zero access to phone calls.

## Path 1 — Speakerphone + mic (MVP, fastest)
Phone on speaker, app listens via mic, transcribes, analyses. Works on any call, needs no one's permission. Fragile: background noise, echo, speaker must stay on, battery drain. A starting point, not the end state.

## Path 2 — VoIP dialer app (the real product)
A calling app that carries the call itself (like WhatsApp calls). Audio is ours, so transcription + verdict run at full quality with no speakerphone. Limitation: only covers calls made through the app, not regular cellular calls.

## Path 3 — Carrier / OEM integration (the scale play)
Protection inside the phone's own dialer (Pixel Call Screen model). The OEM/carrier has system-level call audio access, so every call is covered with nothing for the user to change. Needs partnerships — months of business work, not a weekend build.

## Order
Speakerphone MVP → VoIP dialer → carrier/OEM. The current demo proves the detection engine; a live call only changes where the transcript comes from.
