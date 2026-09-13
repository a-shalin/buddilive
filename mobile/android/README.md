# BuddiLive Mobile (Android)

Native Android client for a simplified BuddiLive workflow:
- sign in with email/username + password
- accounts + balances screen
- account transactions screen with create/update/delete

## Offline use

After signing in, the app downloads accounts and balances, transaction category selectors,
formatting preferences, description suggestions, and the latest 100 transactions for every
account. The download status disappears when preparation finishes. Enable **Stay logged in**
to reopen directly from local data, including when the session has expired.

Refresh spins while synchronization runs; cached screens and new transaction entry remain
usable. New transactions, including edits and deletion before synchronization completes,
are saved on the device. Changes to previously synchronized transactions require the backend.
Sync runs while the app is visible, on reconnect/resume and manual refresh, with retries after
15, 30, then 60 seconds following transient failures. An expired session pauses sync and offers
**Sign in to sync**, while local data and entry remain available.

Caches and pending transactions are isolated by backend and login. Existing caches are retained
on upgrade. If older pending transactions have no recorded owner, sign in to their original
account and use **Restore saved transactions** to confirm ownership before uploading them.

Focused offline verification:

```bash
./gradlew :app:testDebugUnitTest --tests '*BuddiRepositoryTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ca.digitalcave.buddilive.mobile.OfflineStartupTest
```

## Base URL policy

- Debug/local backend: `http://10.0.2.2:8081/`
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

Start or stop the default emulator:

```bash
cd /home/ashalin/git/buddilive
./scripts/start-android-emulator.sh
./scripts/stop-android-emulator.sh
```

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
