#!/usr/bin/env python3
"""Install Capacitor Java plugin in generated Android project. Cloud build helper."""
from pathlib import Path
import shutil
root=Path(__file__).resolve().parent.parent
java_dir=root/'android/app/src/main/java/com/maher/kaitv32'
java_dir.mkdir(parents=True,exist_ok=True)
main=java_dir/'MainActivity.java'
assert (root/'android/app/build.gradle').exists(), 'Run npx cap add android first'
shutil.copy2(root/'native/KaitLanHostPlugin.java',java_dir/'KaitLanHostPlugin.java')
main.write_text("""package com.maher.kaitv32;

import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.view.View;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  private void immersive() {
    getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
      View.SYSTEM_UI_FLAG_FULLSCREEN |
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  }
  @Override public void onCreate(Bundle savedInstanceState) {
    registerPlugin(KaitLanHostPlugin.class);
    super.onCreate(savedInstanceState);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    immersive();
  }
  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) immersive();
  }
}
""")
gradle=root/'android/app/build.gradle'
g=gradle.read_text()
if 'org.java-websocket:Java-WebSocket' not in g:
    g+="\n// Kait local game hosting\ndependencies { implementation 'org.java-websocket:Java-WebSocket:1.5.7' }\n"
    gradle.write_text(g)
manifest=root/'android/app/src/main/AndroidManifest.xml'
x=manifest.read_text()
if 'android.permission.ACCESS_WIFI_STATE' not in x:
    x=x.replace('<application','<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />\n<application',1)
if 'android.permission.INTERNET' not in x:
    x=x.replace('<application','<uses-permission android:name="android.permission.INTERNET" />\n<application',1)
if 'android:usesCleartextTraffic' not in x:
    x=x.replace('<application','<application android:usesCleartextTraffic="true"',1)
manifest.write_text(x)
print('Kait Android native hotspot/LAN host added')

# App display name and packaged launcher artwork.
res=root/'android/app/src/main/res'
for values in [res/'values/strings.xml']:
    if values.exists():
        content=values.read_text().replace('Kait V2','কাইত')
        values.write_text(content)
# Java resource names vary across Capacitor versions. Supply the same artwork
# at every launcher density while retaining Android's existing adaptive-icon XML.
from PIL import Image
icon=root/'native/kait-icon.png'
if icon.exists():
    art=Image.open(icon).convert('RGBA')
    for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        target=res/('mipmap-'+density)
        if target.exists():
            for name in ['ic_launcher.png','ic_launcher_round.png','ic_launcher_foreground.png']:
                if (target/name).exists():art.resize((px,px),Image.Resampling.LANCZOS).save(target/name)
# Capacitor 8 adaptive icons may refer to drawable resources rather than mipmap PNGs.
    # Override the existing adaptive foreground and background resources when present.
    for folder in res.glob('drawable*'):
        if folder.is_dir():
            for target in folder.glob('ic_launcher_foreground.png'):
                art.resize((432,432),Image.Resampling.LANCZOS).save(target)
            for target in folder.glob('ic_launcher_background.png'):
                Image.new('RGBA',(432,432),'#073528').save(target)
print('Kait branding applied')
