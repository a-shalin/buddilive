# BuddiLive Mobile (Android)

Native Android client for a simplified BuddiLive workflow:
- sign in with email/username + password
- accounts + balances screen
- account transactions screen with create/update/delete

## Base URL policy

- Debug/standalone build: `http://10.0.2.2:8080/`
- Debug build cleartext policy: allowed only for `10.0.2.2`, `localhost`, and `127.0.0.1`
- Release build: `https://<domain_name>/` where `domain_name` is read from `ansible/inventory/production.ini`

Release builds fail if `domain_name` is missing.

## Build

```bash
cd mobile/android
./gradlew assembleDebug
./gradlew assembleRelease
```

## Android E2E (emulator + backend)

Run backend + Android test in one command:

```bash
cd /home/ashalin/git/buddilive
./run-android-e2e-tests.sh
```

Or run manually in two terminals.

Start backend with a fresh DB:

```bash
cd /home/ashalin/git/buddilive
./run-android-e2e-backend.sh
```

Run Android instrumentation test:

```bash
cd /home/ashalin/git/buddilive/mobile/android
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ca.digitalcave.buddilive.mobile.StandaloneBackendE2eTest
```

## Publish APK to backend static path

```bash
cd mobile/android
./publish-apk.sh
```

This copies `app/build/outputs/apk/release/app-release.apk` to:
`src/main/resources/static/mobile/buddilive-android.apk`
