#!/bin/bash
# Fetch the bundled Vosk voice models into app/src/main/assets/models/.
# Run once before building: the APK bundles these zips so the app needs
# NO network download on the phone — first launch installs them itself.
# Models are ~40MB each, Apache 2.0, from alphacephei.com/vosk/models.
set -e
cd "$(dirname "$0")"
mkdir -p app/src/main/assets/models
curl -fsSL https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip \
  -o app/src/main/assets/models/vosk-model-en.zip
curl -fsSL https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip \
  -o app/src/main/assets/models/vosk-model-hi.zip
ls -lh app/src/main/assets/models/
echo "Done — build the APK in Android Studio (or gradle assembleDebug)."
