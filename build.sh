#!/bin/sh
# Builds the sideloadable APK without Gradle, with Ubuntu's Android tools:
#   apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
# Output: build/MeteoAlarmAndroid-v<version>.apk, signed with $KEYSTORE (default ./debug.keystore,
# password "android"; kept out of the repository).
# Preview of the screen on a PC (no phone needed): ./build.sh preview
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
# The resources are compiled against a newer platform (API 33) for the widget attributes of Android
# 12+ (widgetFeatures reconfigurable / configuration_optional, targetCell*, maxResize*), and since
# v2.00 the Java code too (notification channels, POST_NOTIFICATIONS; newer calls are guarded by
# Build.VERSION.SDK_INT). RES_JAR=path/to/android-33.jar (any API 33 android.jar of the SDK).
RES_JAR=${RES_JAR:-$SDK}
VER=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

if [ "$1" = preview ]; then
	shift
	javac -nowarn -encoding UTF-8 -d "$OUT/classes" $(find src/ro/meteoalarm/android/core preview -name '*.java')
	java -cp "$OUT/classes" ro.meteoalarm.android.preview.Preview assets "$OUT/preview.png" 1080 2340 "$@"
	exit 0
fi

aapt package -f -M AndroidManifest.xml -S res -I "$RES_JAR" -J "$OUT/gen" -m
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$RES_JAR" -d "$OUT/classes" \
	$(find src "$OUT/gen" -name '*.java')
dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes"
# resources.arsc stays uncompressed (required from targetSdk 30).
aapt package -f -0 arsc -M AndroidManifest.xml -S res -A assets -I "$RES_JAR" -F "$OUT/app.unsigned.apk"
(cd "$OUT" && aapt add -f app.unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"
# The signing key is not in the repository. Every version must be signed with the same key, or it
# will not install over (or update) the one on the phone: KEYSTORE=path/to/debug.keystore ./build.sh
KS=${KEYSTORE:-debug.keystore}
if [ ! -f "$KS" ]; then
	echo "No signing key at $KS (set KEYSTORE=...)." >&2
	exit 1
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
	--out "$OUT/MeteoAlarmAndroid-v$VER.apk" "$OUT/app.aligned.apk"
apksigner verify --print-certs "$OUT/MeteoAlarmAndroid-v$VER.apk" | head -n 2
ls -l "$OUT/MeteoAlarmAndroid-v$VER.apk"
