# FocusGuard (Expo + native Android module)

## Setup
```bash
npx create-expo-app@latest focusguard --template blank-typescript
cd focusguard
# copy this project's files over the generated ones (App.tsx, app.json, plugins/, modules/)
npx expo install expo-document-picker expo-modules-core
npx expo prebuild --platform android --clean
npx expo run:android          # requires Android SDK + device/emulator (NOT Expo Go)
```
Then open the app, grant **Usage Access** and **Draw over other apps**, pick apps, flip **Monitoring** on.

## Privacy
- No INTERNET permission, no analytics/crash SDKs, no network code.
- Config, usage and alert log: Android SharedPreferences (app-private).
- Image/audio: copied into the app's private `filesDir`.
