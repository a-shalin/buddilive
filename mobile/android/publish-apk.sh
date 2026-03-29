#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

SOURCE_APK_SIGNED="app/build/outputs/apk/release/app-release.apk"
SOURCE_APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
TARGET_APK="../../src/main/resources/static/mobile/buddilive-android.apk"

if [ -f "$SOURCE_APK_SIGNED" ]; then
	SOURCE_APK="$SOURCE_APK_SIGNED"
elif [ -f "$SOURCE_APK_UNSIGNED" ]; then
	SOURCE_APK="$SOURCE_APK_UNSIGNED"
else
	echo "Release APK not found:"
	echo "  - $SOURCE_APK_SIGNED"
	echo "  - $SOURCE_APK_UNSIGNED"
	echo "Build it first with: ./gradlew assembleRelease"
	exit 1
fi

mkdir -p "$(dirname "$TARGET_APK")"
cp "$SOURCE_APK" "$TARGET_APK"

echo "APK published to $TARGET_APK"
