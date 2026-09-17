# Build and verification

## Local checks

The fast verification layers are:

```powershell
./gradlew.bat :core:test :workout-agent:test --no-daemon
./gradlew.bat :app:assembleDebug --no-daemon
```

The first command exercises the pure business rules and the real JDBC/SQLite headless workflow. The second produces:

`app/build/outputs/apk/debug/app-debug.apk`

That APK is a debug build suitable for local installation on an Android phone. It is not a signed release build.

## Headless process check

Package and run the actual JSONL process on Windows with:

```powershell
./gradlew.bat :workout-agent:installDist --no-daemon
./scripts/headless-smoke.ps1
```

The smoke script creates a temporary database, launches the generated agent executable, drives a complete workout over stdin, verifies progressive overload through structured state, and removes its temporary data.

## CI

`.github/workflows/verify.yml` runs the JVM tests on every push and pull request, builds the debug APK, and uploads it as the `workoutapp-debug-apk` artifact. Downloading that artifact from a workflow run is the intended phone-install path until a release-signing process is added.

## Evidence boundaries

The repository currently verifies business behavior, real SQLite persistence/restart, the packaged headless process, and APK compilation. It does not claim emulator/instrumentation coverage or physical-device installation until an Android runtime is available for that check.
