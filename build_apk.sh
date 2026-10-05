#!/data/data/com.termux/files/usr/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_JAR="/data/data/com.termux/files/usr/share/java/android.jar"
FRAMEWORK_RES="/system/framework/framework-res.apk"
BUILD_DIR="$DIR/build_termux"
OBJ_DIR="$BUILD_DIR/obj"
APK_DIR="$BUILD_DIR/apk"
GEN_DIR="$BUILD_DIR/gen"
RES_DIR="$BUILD_DIR/res"

rm -rf "$OBJ_DIR" "$APK_DIR" "$GEN_DIR" "$RES_DIR"
mkdir -p "$OBJ_DIR" "$APK_DIR" "$GEN_DIR" "$RES_DIR/values"

cat << 'EOF' > "$RES_DIR/values/strings.xml"
<resources>
    <string name="app_name">Pointage CFPM</string>
</resources>
EOF

cat << 'EOF' > "$RES_DIR/values/themes.xml"
<resources>
    <style name="Theme.Pointage" parent="@android:style/Theme.DeviceDefault.NoActionBar">
        <item name="android:statusBarColor">#0E5A3C</item>
        <item name="android:navigationBarColor">#FFFFFF</item>
    </style>
    <color name="ic_launcher_background">#0E5A3C</color>
</resources>
EOF

cp -r "$DIR/app/src/main/res/drawable" "$RES_DIR/"
cp -r "$DIR/app/src/main/res/mipmap-anydpi" "$RES_DIR/"

cat << 'EOF' > "$BUILD_DIR/AndroidManifest.xml"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.gbeauto237.pointage"
    android:versionCode="1"
    android:versionName="1.0">

    <uses-sdk
        android:minSdkVersion="21"
        android:targetSdkVersion="34" />

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />

    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.camera.front" android:required="false" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />

    <supports-screens
        android:smallScreens="true"
        android:normalScreens="true"
        android:largeScreens="true"
        android:xlargeScreens="true"
        android:anyDensity="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Pointage"
        android:resizeableActivity="true"
        android:hardwareAccelerated="true">

        <activity
            android:name="com.gbeauto237.pointage.MainActivity"
            android:exported="true"
            android:resizeableActivity="true"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

echo "=== 1. Generating R.java with aapt ==="
aapt package -f -m \
    --min-sdk-version 21 \
    --target-sdk-version 34 \
    --version-code 1 \
    --version-name "1.0" \
    -J "$GEN_DIR" \
    -M "$BUILD_DIR/AndroidManifest.xml" \
    -S "$RES_DIR" \
    -I "$FRAMEWORK_RES"

echo "=== 2. Compiling Java sources with javac ==="
javac -source 8 -target 8 -Xlint:-options \
    -cp "$ANDROID_JAR" \
    -d "$OBJ_DIR" \
    $(find "$DIR/app/src/main/java" "$GEN_DIR" -name "*.java")

echo "=== 3. Dexing with d8 ==="
d8 --output "$APK_DIR" \
    --min-api 21 \
    --lib "$ANDROID_JAR" \
    $(find "$OBJ_DIR" -name "*.class")

echo "=== 4. Packaging APK with aapt ==="
aapt package -f \
    --min-sdk-version 21 \
    --target-sdk-version 34 \
    --version-code 1 \
    --version-name "1.0" \
    -M "$BUILD_DIR/AndroidManifest.xml" \
    -S "$RES_DIR" \
    -A "$DIR/app/src/main/assets" \
    -I "$FRAMEWORK_RES" \
    -F "$BUILD_DIR/PointageCFPM_unsigned.apk"

cd "$APK_DIR"
aapt add "$BUILD_DIR/PointageCFPM_unsigned.apk" classes.dex
cd "$DIR"

echo "=== 5. Zipalign APK ==="
zipalign -f -p 4 "$BUILD_DIR/PointageCFPM_unsigned.apk" "$BUILD_DIR/PointageCFPM_aligned.apk"

KEYSTORE="$BUILD_DIR/release.keystore"
if [ ! -f "$KEYSTORE" ]; then
    echo "=== 6. Generating Signing Key ==="
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias pointage \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass pointage2026 -keypass pointage2026 \
        -dname "CN=CFPM, OU=Automotive, O=GBE_AUTO_237, L=Douala, ST=LT, C=CM"
fi

echo "=== 7. Signing APK with apksigner (v1, v2, v3) ==="
FINAL_APK="$DIR/PointageCFPM.apk"
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias pointage \
    --ks-pass pass:pointage2026 \
    --key-pass pass:pointage2026 \
    --min-sdk-version 21 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$FINAL_APK" \
    "$BUILD_DIR/PointageCFPM_aligned.apk"

echo "=== 8. Verifying Signature and Badging ==="
apksigner verify --verbose "$FINAL_APK"

# Copy to accessible storage paths
mkdir -p "/storage/emulated/0/Download" 2>/dev/null || true
cp -f "$FINAL_APK" "/storage/emulated/0/Download/PointageCFPM.apk" 2>/dev/null || true
cp -f "$FINAL_APK" "/storage/emulated/0/a Heur d'arriver/PointageCFPM.apk" 2>/dev/null || true
cp -f "$FINAL_APK" "/storage/emulated/0/PointageCFPM.apk" 2>/dev/null || true

echo "=== SUCCESS! APK GENERATED AT: $FINAL_APK ==="
echo "=== Target SDK: 34 (Android 14) | Min SDK: 21 (Android 5.0+) ==="
