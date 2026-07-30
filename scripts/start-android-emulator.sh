#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AVD_NAME="${ANDROID_AVD:-Medium_Phone}"
HEADLESS="${ANDROID_EMULATOR_HEADLESS:-1}"
VISIBLE_GPU="${ANDROID_EMULATOR_GPU:-swiftshader_indirect}"
BOOT_TIMEOUT_SECONDS="${ANDROID_EMULATOR_BOOT_TIMEOUT_SECONDS:-360}"
LOG_FILE="${ANDROID_EMULATOR_LOG:-/tmp/buddilive-android-emulator-${AVD_NAME}.log}"
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
if [[ -z "$SDK_DIR" ]]; then
	echo "Android SDK not found. Set ANDROID_HOME, ANDROID_SDK_ROOT, or mobile/android/local.properties sdk.dir." >&2
	exit 1
fi

ADB="${ADB:-$SDK_DIR/platform-tools/adb}"
EMULATOR="${ANDROID_EMULATOR:-$SDK_DIR/emulator/emulator}"

if [[ ! -x "$ADB" ]] && command -v adb >/dev/null 2>&1; then
	ADB="$(command -v adb)"
fi
if [[ ! -x "$EMULATOR" ]] && command -v emulator >/dev/null 2>&1; then
	EMULATOR="$(command -v emulator)"
fi
if [[ ! -x "$ADB" ]]; then
	echo "adb not found at $ADB." >&2
	exit 1
fi
if [[ ! -x "$EMULATOR" ]]; then
	echo "Android emulator not found at $EMULATOR." >&2
	exit 1
fi

connected_emulator_serial() {
	"$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

wait_for_boot() {
	local serial="$1"
	local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))

	while (( SECONDS < deadline )); do
		if [[ -n "${EMULATOR_PID:-}" ]] && ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
			echo "Android emulator exited before boot completed. See $LOG_FILE" >&2
			tail -n 120 "$LOG_FILE" >&2 || true
			exit 1
		fi

		local boot_completed
		boot_completed="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
		if [[ "$boot_completed" == "1" ]]; then
			"$ADB" -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
			return
		fi

		sleep 2
	done

	echo "Timed out waiting for Android emulator boot. See $LOG_FILE" >&2
	tail -n 120 "$LOG_FILE" >&2 || true
	exit 1
}

"$ADB" start-server >/dev/null

SERIAL="$(connected_emulator_serial)"
if [[ -n "$SERIAL" ]]; then
	echo "Reusing Android emulator $SERIAL."
	wait_for_boot "$SERIAL"
	echo "Android emulator is ready: $SERIAL"
	exit 0
fi

if ! "$EMULATOR" -list-avds | grep -Fx "$AVD_NAME" >/dev/null; then
	echo "AVD '$AVD_NAME' was not found. Set ANDROID_AVD to an existing AVD." >&2
	exit 1
fi

EMULATOR_ARGS=(-avd "$AVD_NAME" -no-snapshot -no-snapshot-save -no-boot-anim -no-metrics)
ACCEL_CHECK="$("$EMULATOR" -accel-check 2>&1 || true)"
if printf '%s\n' "$ACCEL_CHECK" | grep -qi '/dev/kvm is not found'; then
	EMULATOR_ARGS+=(-accel off)
	echo "KVM acceleration is unavailable; starting emulator with software acceleration." >&2
fi
if [[ "$HEADLESS" == "1" ]]; then
	EMULATOR_ARGS+=(-no-window -no-audio -gpu swiftshader_indirect)
	echo "Starting Android emulator '$AVD_NAME' headlessly. Log: $LOG_FILE"
else
	EMULATOR_ARGS+=(-gpu "$VISIBLE_GPU")
	echo "Starting Android emulator '$AVD_NAME' with GPU '$VISIBLE_GPU'. Log: $LOG_FILE"
fi

if command -v setsid >/dev/null 2>&1; then
	nohup setsid "$EMULATOR" "${EMULATOR_ARGS[@]}" >"$LOG_FILE" 2>&1 &
else
	nohup "$EMULATOR" "${EMULATOR_ARGS[@]}" >"$LOG_FILE" 2>&1 &
fi
EMULATOR_PID="$!"
printf '%s\n' "$EMULATOR_PID" >"$PID_FILE"

for _ in $(seq 1 60); do
	SERIAL="$(connected_emulator_serial)"
	if [[ -n "$SERIAL" ]]; then
		break
	fi
	if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
		echo "Android emulator exited before connecting to adb. See $LOG_FILE" >&2
		tail -n 120 "$LOG_FILE" >&2 || true
		exit 1
	fi
	sleep 1
done

if [[ -z "$SERIAL" ]]; then
	echo "Android emulator did not connect to adb in time. See $LOG_FILE" >&2
	tail -n 120 "$LOG_FILE" >&2 || true
	exit 1
fi

wait_for_boot "$SERIAL"
echo "Android emulator is ready: $SERIAL"
