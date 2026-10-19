$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$javaDir = Join-Path $root 'android\app\src\main\java\com\maher\kaitv32'
$main = Join-Path $javaDir 'MainActivity.java'
$gradle = Join-Path $root 'android\app\build.gradle'
$manifest = Join-Path $root 'android\app\src\main\AndroidManifest.xml'
if (!(Test-Path $main)) { throw 'Run npx cap add android first; MainActivity.java not found.' }
Copy-Item (Join-Path $root 'native\KaitLanHostPlugin.java') (Join-Path $javaDir 'KaitLanHostPlugin.java') -Force
@'
package com.maher.kaitv32;

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
'@ | Set-Content $main -Encoding utf8
$g = Get-Content $gradle -Raw
if ($g -notmatch 'org.java-websocket:Java-WebSocket') {
  $g += "`n// Local phone-hosted Kait rooms`ndependencies { implementation 'org.java-websocket:Java-WebSocket:1.5.7' }`n"
  Set-Content $gradle $g -Encoding utf8
}
$x = Get-Content $manifest -Raw
if ($x -notmatch 'android.permission.INTERNET') {$x=$x.Replace('<application', '<uses-permission android:name="android.permission.INTERNET" />' + "`n" + '<application')}
if ($x -notmatch 'usesCleartextTraffic') {$x=$x.Replace('<application','<application android:usesCleartextTraffic="true"')}
Set-Content $manifest $x -Encoding utf8
if ($x -notmatch 'android.permission.ACCESS_WIFI_STATE') {$x=$x.Replace('<application', '<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />' + "`n" + '<application')}
Set-Content $manifest $x -Encoding utf8
Write-Host 'Kait native LAN host installed. Next: npx cap sync android; npx cap open android'
