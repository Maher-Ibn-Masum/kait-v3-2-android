# Build and test Kait V3.2

1. Make a new **private** repository named `kait-v3-2-android` on GitHub.
2. Extract the ZIP and upload its contents directly to the repository root, including hidden `.github/workflows/android-apk.yml`.
3. Under Actions, run **Build Kait V3.2 Android APK**.
4. When green, download **kait-v3-2-integrated-debug-apk** from the run Summary -> Artifacts.
5. Extract the artifact ZIP to get `app-debug.apk` and install it on your phone(s).
6. Test solo mode on one phone: shuffle, guess, bet, Ready, deal by swipe, confirm round result, inspect History.
7. Test online mode on two devices and then nearby mode on hotspot with both mobile data connections off. Use Scan first, manual host IP/code if needed.
8. To inspect UI on Windows without compiling, serve the `www` folder using `python -m http.server 8000` and visit `http://localhost:8000`. This does not implement native nearby hosting.

Superhero identities/avatars are **not changed** from the playable game. 20-player performance needs a real group test. Keep the old APK available until verified.
