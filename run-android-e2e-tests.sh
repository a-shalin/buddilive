#!/bin/bash
# Run Android instrumentation E2E against a fresh backend and stop backend afterward.

set -euo pipefail
cd "$(dirname "$0")"

BACKEND_LOG="${BACKEND_LOG:-/tmp/buddilive-android-e2e-backend.log}"
BACKEND_PID=""
BACKEND_READY_TEXT="Started BuddiSpringApplication"
BACKEND_FAILED_TEXT="APPLICATION FAILED TO START"

cleanup() {
	if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
		kill "$BACKEND_PID" >/dev/null 2>&1 || true
		wait "$BACKEND_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT INT TERM

./run-android-e2e-backend.sh >"$BACKEND_LOG" 2>&1 &
BACKEND_PID="$!"

for _ in $(seq 1 180); do
	if grep -q "$BACKEND_FAILED_TEXT" "$BACKEND_LOG"; then
		echo "Backend failed to start. See $BACKEND_LOG"
		tail -n 120 "$BACKEND_LOG" || true
		exit 1
	fi
	if grep -q "$BACKEND_READY_TEXT" "$BACKEND_LOG" && curl -fsS --max-time 2 http://localhost:8080/ >/dev/null 2>&1; then
		break
	fi
	if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
		echo "Backend exited before becoming ready. See $BACKEND_LOG"
		tail -n 120 "$BACKEND_LOG" || true
		exit 1
	fi
	sleep 1
done

if ! grep -q "$BACKEND_READY_TEXT" "$BACKEND_LOG" || ! curl -fsS --max-time 2 http://localhost:8080/ >/dev/null 2>&1; then
	echo "Backend did not become ready in time. See $BACKEND_LOG"
	tail -n 120 "$BACKEND_LOG" || true
	exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
	echo "adb is required to run Android E2E tests."
	exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
	echo "No Android device or emulator is connected."
	exit 1
fi

AIRPLANE_MODE="$(adb shell settings get global airplane_mode_on 2>/dev/null | tr -d '\r')"
if [ "$AIRPLANE_MODE" = "1" ]; then
	echo "The connected emulator has airplane mode enabled; disable it before running Android E2E tests."
	exit 1
fi

EMULATOR_BACKEND_RESPONSE="$(adb shell 'printf \"GET / HTTP/1.0\\r\\n\\r\\n\" | nc -w 5 10.0.2.2 8080 | head -n 1' 2>/dev/null | tr -d '\r')"
if [[ "$EMULATOR_BACKEND_RESPONSE" != HTTP/* ]]; then
	echo "Backend is ready on the host, but the emulator cannot reach http://10.0.2.2:8080/."
	echo "First response line from emulator: ${EMULATOR_BACKEND_RESPONSE:-<none>}"
	echo "Emulator route table:"
	adb shell ip route || true
	exit 1
fi

cd mobile/android
./gradlew --no-daemon :app:connectedDebugAndroidTest \
	-Pandroid.testInstrumentationRunnerArguments.class=ca.digitalcave.buddilive.mobile.StandaloneBackendE2eTest \
	"$@"
