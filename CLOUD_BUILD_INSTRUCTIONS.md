# কাইত V3.2 · GitHub Actions Android APK

1. Create a new PRIVATE GitHub repository named `kait-v3-2-android`.
2. Extract `kait-v3-2-integrated-android-source.zip` and upload its CONTENTS to the repository root, preserving `.github/workflows/android-apk.yml`, `native`, `scripts`, and `www`.
3. Open GitHub Actions. Choose **Build Kait V3.2 Android APK** and run the workflow, or wait for it to run after commit.
4. After the workflow succeeds, open the run Summary > Artifacts and download **kait-v3-2-integrated-debug-apk**.
5. Extract the artifact ZIP and install `app-debug.apk` on your Android phone(s).

You do not need Node.js or Android Studio on your computer. V3.2's package ID is `com.maher.kaitv32` and coexists with older Kait APKs.

Test offline bots and the History button on one phone first. Test online and nearby multiplayer with two V3.2 APK installations. Native hotspot hosting must be tested on Android; the browser preview only tests the non-native interface and bots.
