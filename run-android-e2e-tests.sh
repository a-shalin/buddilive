#!/bin/bash
# Run Android instrumentation E2E against a fresh backend and stop backend afterward.

set -euo pipefail
cd "$(dirname "$0")"

BACKEND_LOG="${BACKEND_LOG:-/tmp/buddilive-android-e2e-backend.log}"
BACKEND_PORT="8081"
BACKEND_URL="http://localhost:${BACKEND_PORT}/"
EMULATOR_BACKEND_HOST="10.0.2.2"
BACKEND_PID=""
BACKEND_READY_TEXT="Started BuddiSpringApplication"
BACKEND_FAILED_TEXT="APPLICATION FAILED TO START"
START_EMULATOR="${ANDROID_E2E_START_EMULATOR:-1}"

cleanup() {
	if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
		kill "$BACKEND_PID" >/dev/null 2>&1 || true
		wait "$BACKEND_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT INT TERM

if [ "$START_EMULATOR" = "1" ]; then
	./scripts/start-android-emulator.sh
fi

./run-android-e2e-backend.sh >"$BACKEND_LOG" 2>&1 &
BACKEND_PID="$!"

for _ in $(seq 1 180); do
	if grep -q "$BACKEND_FAILED_TEXT" "$BACKEND_LOG"; then
		echo "Backend failed to start. See $BACKEND_LOG"
		tail -n 120 "$BACKEND_LOG" || true
		exit 1
	fi
	if grep -q "$BACKEND_READY_TEXT" "$BACKEND_LOG" && curl -fsS --max-time 2 "$BACKEND_URL" >/dev/null 2>&1; then
		break
	fi
	if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
		echo "Backend exited before becoming ready. See $BACKEND_LOG"
		tail -n 120 "$BACKEND_LOG" || true
		exit 1
	fi
	sleep 1
done

if ! grep -q "$BACKEND_READY_TEXT" "$BACKEND_LOG" || ! curl -fsS --max-time 2 "$BACKEND_URL" >/dev/null 2>&1; then
	echo "Backend did not become ready in time. See $BACKEND_LOG"
	tail -n 120 "$BACKEND_LOG" || true
	exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
	echo "adb is required to run Android E2E tests."
	exit 1
fi

if [ -z "${ANDROID_SERIAL:-}" ]; then
	ANDROID_SERIAL="$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
fi
if [ -z "$ANDROID_SERIAL" ]; then
	echo "No Android emulator is connected."
	exit 1
fi

adb_cmd() {
	adb -s "$ANDROID_SERIAL" "$@"
}

if ! adb_cmd get-state >/dev/null 2>&1; then
	echo "Android emulator $ANDROID_SERIAL is not connected."
	exit 1
fi

AIRPLANE_MODE="$(adb_cmd shell settings get global airplane_mode_on 2>/dev/null | tr -d '\r')"
if [ "$AIRPLANE_MODE" = "1" ]; then
	echo "The connected emulator has airplane mode enabled; disable it before running Android E2E tests."
	exit 1
fi

EMULATOR_BACKEND_RESPONSE="$(adb_cmd shell "printf \"GET / HTTP/1.0\\r\\n\\r\\n\" | nc -w 5 ${EMULATOR_BACKEND_HOST} ${BACKEND_PORT} | head -n 1" 2>/dev/null | tr -d '\r')"
if [[ "$EMULATOR_BACKEND_RESPONSE" != HTTP/* ]]; then
	echo "Backend is ready on the host, but the emulator cannot reach http://${EMULATOR_BACKEND_HOST}:${BACKEND_PORT}/."
	echo "First response line from emulator: ${EMULATOR_BACKEND_RESPONSE:-<none>}"
	echo "Emulator route table:"
	adb_cmd shell ip route || true
	exit 1
fi

cd mobile/android
ANDROID_SERIAL="$ANDROID_SERIAL" ./gradlew --no-daemon :app:connectedDebugAndroidTest \
	-Pandroid.testInstrumentationRunnerArguments.class=ca.digitalcave.buddilive.mobile.StandaloneBackendE2eTest \
	"$@"
