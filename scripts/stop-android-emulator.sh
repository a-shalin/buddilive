#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AVD_NAME="${ANDROID_AVD:-Medium_Phone}"
PID_FILE="${ANDROID_EMULATOR_PID_FILE:-/tmp/buddilive-android-emulator-${AVD_NAME}.pid}"

find_android_sdk() {
	if [[ -n "${ANDROID_HOME:-}" ]]; then
		printf '%s\n' "$ANDROID_HOME"
		return
	fi
	if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
		printf '%s\n' "$ANDROID_SDK_ROOT"
		return
	fi
	if [[ -f "$ROOT_DIR/mobile/android/local.properties" ]]; then
		sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/mobile/android/local.properties" | tail -n 1
		return
	fi
	if [[ -f "$ROOT_DIR/local.properties" ]]; then
		sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" | tail -n 1
	fi
}

SDK_DIR="$(find_android_sdk)"
ADB="${ADB:-${SDK_DIR:+$SDK_DIR/platform-tools/adb}}"
if [[ -z "$ADB" || ! -x "$ADB" ]] && command -v adb >/dev/null 2>&1; then
	ADB="$(command -v adb)"
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
	SERIAL="$ANDROID_SERIAL"
elif [[ -n "${ADB:-}" && -x "$ADB" ]]; then
	SERIAL="$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
else
	SERIAL=""
fi

if [[ -n "$SERIAL" ]]; then
	echo "Stopping Android emulator $SERIAL."
	"$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
	for _ in $(seq 1 30); do
		if ! "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; then
			rm -f "$PID_FILE"
			echo "Android emulator stopped."
			exit 0
		fi
		sleep 1
	done
fi

if [[ -f "$PID_FILE" ]]; then
	PID="$(cat "$PID_FILE")"
	if [[ -n "$PID" ]] && kill -0 "$PID" >/dev/null 2>&1; then
		echo "Stopping Android emulator process $PID."
		kill "$PID" >/dev/null 2>&1 || true
	fi
	rm -f "$PID_FILE"
	echo "Android emulator stopped."
	exit 0
fi

echo "No Android emulator was running."
