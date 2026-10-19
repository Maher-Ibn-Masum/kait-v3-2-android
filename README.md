# কাইত V3.2 — Integrated Private Friends Edition

This is a **playable** build derived from the V3.1 game implementation, with the new V3.2 artwork and responsive navigation integrated into the original rules engine. Original superhero names and automatic avatar identities are preserved.

## Modes
- **Offline bots:** runs entirely locally, human approval controls betting; round results do not auto-advance.
- **Online multiplayer:** uses your Render signaling relay at kait-server.onrender.com.
- **Nearby:** native Android WebSocket host/client and local-room discovery; the proven manual IP fallback remains available.

Room-scoped Round History, Ready/Pass, manual/custom stakes, 20 players, rightward turn rotation, shuffle, audio and swipe-to-deal are retained.

## Test and limitations
This source can be packaged by the included GitHub Actions workflow. Browser-mode Nearby hosting requires the Android native plugin. External servers and physical multiplayer cannot be validated by a local browser test. Android build and two-phone testing remain necessary.

## Build
Create a **new private GitHub repository**, extract this package, upload its contents to the repository root (including `.github/workflows/android-apk.yml`), run `Build Kait V3.2 Android APK` in Actions, download the debug APK artifact and install on your Android devices. V2, V3 and V3.1 remain unchanged.

## Important
The history exists only for the lifetime of the room. No database or export. The visual look was tested with synthetic UI actions in Chromium; the real network flow must be verified on your phones.
