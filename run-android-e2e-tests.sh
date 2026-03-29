#!/bin/bash
# Run Android instrumentation E2E against a fresh backend and stop backend afterward.

set -euo pipefail
cd "$(dirname "$0")"

BACKEND_LOG="${BACKEND_LOG:-/tmp/buddilive-android-e2e-backend.log}"
BACKEND_PID=""

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
	if curl -fsS --max-time 2 http://localhost:8080/ >/dev/null 2>&1; then
		break
	fi
	if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
		echo "Backend exited before becoming ready. See $BACKEND_LOG"
		tail -n 120 "$BACKEND_LOG" || true
		exit 1
	fi
	sleep 1
done

if ! curl -fsS --max-time 2 http://localhost:8080/ >/dev/null 2>&1; then
	echo "Backend did not become ready in time. See $BACKEND_LOG"
	tail -n 120 "$BACKEND_LOG" || true
	exit 1
fi

cd mobile/android
./gradlew --no-daemon :app:connectedDebugAndroidTest \
	-Pandroid.testInstrumentationRunnerArguments.class=ca.digitalcave.buddilive.mobile.StandaloneBackendE2eTest \
	"$@"
