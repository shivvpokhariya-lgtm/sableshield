# SableShield

> 🚧 **In development.** SableShield is a live scam-call detector: it warns you **during** a scam call, by analysing what the caller actually says — not just their number.

## Live links — unchanged, nothing removed

- **Demo website (how it will work):** https://shivvpokhariya-lgtm.github.io/sableshield/
- **Presentation (explainer deck, 8 slides):** https://shivvpokhariya-lgtm.github.io/sableshield/SableShield-Explainer.html

The original site files still live at the repo root and GitHub Pages still serves them from there — they were not deleted or replaced. The Android app below is an addition, not a replacement.

## What this app is — and what it will become

Today's spam blockers act *after* the fraud: they block numbers that other people already reported. SableShield acts *during* the call. It listens (on-device, privately), transcribes the caller's speech, and flags the tactics scammers rely on — fake urgency ("account blocked in 2 hours"), OTP requests, secrecy ("don't tell anyone"), threats, and credential demands — then shows a verdict banner while the call is still going.

What it is now: a working Android prototype (in `android/`) that proves the full pipeline — on-device speech recognition, live rule-based verdicts, optional Gemma 4 confirmation, and automatic start on incoming calls.

What it will become: a full call companion — live verdicts on every call, scam reporting, and eventually protection that does not depend on speakerphone, via a VoIP dialer and then carrier/OEM integration.

The core promise never changes: **audio never leaves the phone, and only open weights make this legal** — a closed API would send the call to a server, which would itself be the violation.

## Repo layout

- `android/` — the Android app (Kotlin, minSdk 26). Open it in Android Studio and build from the Build menu. Its README has the full architecture, honest limits, and current status.
- `demo/` — the original web demo: the interactive site version (scripted scam/safe call demos, same rule engine, explainer deck). Open `demo/index.html` in a browser.

Note: the original site files also still live at the repo root (GitHub Pages serves them from there); `demo/` is only a copy of them. The root files are staying — no cleanup, no deletion.
