# SableShield Android — live scam-call detector

> 🚧 **In development.** This is a working prototype, not a finished product. It already detects scam patterns live on a phone — the remaining proof is a real-phone call test (see Status below).

## What this app is

A phone that warns you **during** a scam call, before money moves — by listening to what the caller actually says, not just checking their number. Truecaller-style apps block numbers *after* the fraud is reported. SableShield analyses the **content of the call, live**, and flags the tactics scammers use: fake urgency, OTP requests, secrecy ("don't tell anyone"), threats, credential demands.

## What it will become

The goal is a full call companion: live verdicts on every call, a verdict banner over the dialer, scam-number reporting, and eventually protection that does not depend on speakerphone — via a VoIP dialer, then carrier/OEM integration. The prototype below is step one: the speakerphone MVP that proves the whole pipeline works.

## How it works

- **Speech recognition: Vosk, on-device.** The mic stream (16 kHz mono) goes into Vosk models for English (`vosk-model-small-en-us-0.15`) and Hindi (`vosk-model-small-hi-0.22`), run in parallel in Auto mode, so Hinglish is caught too. Audio never leaves the phone. This is deliberately *not* Android's built-in SpeechRecognizer — that one is cloud-based and would send call audio to a server, which breaks the privacy promise.
- **Detection: rule engine + optional Gemma 4.** Every transcript chunk is scored by `ScamEngine` (OTP +40, urgency/block/freeze +25, secrecy +20, password/PIN/card/CVV +30, prize/link/remote-access tools +25, police/legal threats +20). If the user types their own Gemini API key into the app, Gemma 4 (`gemma-4-26b-a4b-it`) confirms the first flag. Without a key, the rule engine works on its own — nothing fails.
- **Call auto-start.** On an incoming call the app starts listening by itself (full-screen-intent path) and slides a verdict banner over the call screen on the first scam signal. Detection stays on for the whole call — there is no silence timeout; 20 seconds of quiet only shows a "still listening" note.
- **Pressure signal, honestly labelled.** Fast + loud speech (over 180 words/min and high volume) shows a "pressured delivery" note. This is pace and volume only — true emotion classification (angry/scared from tone) is **not** built and is not claimed.
- **Start page.** First launch is a plain Get Started screen: an optional scripted demo (scam/safe calls, scored by the real engine — scam demo scores 90% SCAM RISK, 3 flags), a privacy-policy checkbox, then Start, which walks through permissions → overlay → main screen.
- **Recent calls + report.** Last 20 calls from the call log (names from contacts), and reporting a number saves it in an on-device reported set.

## Honest limits (read first)

- No third-party app can tap the cellular call audio stream — Android's screening API sees numbers, not audio. So the app listens through the **microphone while the call is on speaker**. The call must stay on speaker, and noisy surroundings hurt accuracy.
- The app **cannot hang up a call** (that belongs to system apps) — the verdict screen tells the user to hang up.
- A real-phone call test is still pending: this emulator's call simulation never broadcasts the call state to third-party apps, so the auto-start path cannot be proven on an emulator at all.

## Status

Verified on emulator: landing → start page → demo (real-engine verdict) → setup complete → main screen; EN+HI models downloaded; all permissions granted; receiver registered; launcher icon (white `S.` box).

Repo note: the launcher foreground ships here as a vector drawable (`res/drawable/ic_launcher_foreground.xml`) — the same S. box design — so the repo builds with no binary PNG. The emulator build uses a rendered PNG of the same design.

Pending (needs a real Android phone): auto-start on a real call, live verdict from real speech, banner over a real dialer, 20-second silence note live, pressure signal live, Gemma confirmation (needs the user's own key, typed in-app).

## Permissions

Mic (listening), phone state (call start/end), call log (recent calls + incoming number), contacts (names), notifications (call alerts), overlay (verdict banner above the call screen). Sideloading build (direct APK install), not a Play Store submission.

## Build

Open `android/` in Android Studio, let Gradle sync finish (JDK 17+), build from the Build menu, run on a phone. There is no `gradlew` in this repo — Studio builds only. On first launch, grant permissions, download the voice models in-app, and optionally type a Gemini API key into the app's key field.

**Key safety:** never put a key in code or in this repo. A key that reaches a repo counts as public — revoke it and make a new one. In the app, the key lives only in the phone's private prefs and goes nowhere except straight to Google's API.
