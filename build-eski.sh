#!/bin/sh
set -eu
cd "$(dirname "$0")"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
: "${sdk:?Set ANDROID_SDK_ROOT or ANDROID_HOME}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
: "${KEYSTORE_PATH:?Set KEYSTORE_PATH to a keystore with alias key and password android}"
tools="$sdk/build-tools/34.0.0"
android_jar="$sdk/platforms/android-34/android.jar"
out=build/eski
rm -rf "$out"
mkdir -p "$out/classes" "$out/dex" "$out/generated"
run_native() {
    if [ -n "${GLIBC_LOADER:-}" ]; then
        "$GLIBC_LOADER" --library-path "${GLIBC_LIBRARY_PATH:?Set GLIBC_LIBRARY_PATH}" "$@"
    else
        "$@"
    fi
}
run_native "$tools/aapt2" compile --dir app/src/main/res -o "$out/resources.zip"
run_native "$tools/aapt2" link --no-compile-sdk-metadata -I "$android_jar" --manifest app/src/eski/AndroidManifest.xml \
    --java "$out/generated" -R "$out/resources.zip" -o "$out/unsigned.apk"
find app/src/eski/java "$out/generated" -name '*.java' > "$out/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -classpath "$android_jar" -d "$out/classes" @"$out/sources.txt"
"$JAVA_HOME/bin/java" -cp "$tools/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 21 \
    --lib "$android_jar" --output "$out/dex" $(find "$out/classes" -name '*.class')
"$JAVA_HOME/bin/jar" uf "$out/unsigned.apk" -C "$out/dex" classes.dex
run_native "$tools/zipalign" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
"$JAVA_HOME/bin/java" -jar "$tools/lib/apksigner.jar" sign --min-sdk-version 16 --ks "$KEYSTORE_PATH" \
    --ks-key-alias key --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
    --v4-signing-enabled false --out Screen-Eski.apk "$out/aligned.apk"
"$JAVA_HOME/bin/java" -jar "$tools/lib/apksigner.jar" verify --verbose --min-sdk-version 16 Screen-Eski.apk
