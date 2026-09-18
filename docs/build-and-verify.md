# Build and verification

## Local checks

The fast verification layers are:

```powershell
./gradlew.bat :core:test :workout-agent:test --no-daemon
./gradlew.bat :app:assembleDebug --no-daemon
```

The first command exercises the pure business rules and the real JDBC/SQLite headless workflow. The second produces:

`app/build/outputs/apk/debug/app-debug.apk`

That APK is a debug build suitable for local installation on an Android phone. Local builds use the machine's default debug key unless the stable-signing environment variables are configured.

## Headless process check

Package and run the actual JSONL process on Windows with:

```powershell
./gradlew.bat :workout-agent:installDist --no-daemon
./scripts/headless-smoke.ps1
```

The smoke script creates a temporary database, launches the generated agent executable, drives a complete workout over stdin, verifies progressive overload through structured state, and removes its temporary data.

## CI

`.github/workflows/verify.yml` runs the JVM tests on every push and pull request, builds the debug APK, and uploads it as the `workoutapp-debug-apk` artifact. Push builds restore a stable signing key from encrypted GitHub secrets and auto-increment the Android version code, so APKs from successive main-branch runs can update the prior GitHub-installed app. Pull-request builds use the runner's temporary debug key and are for verification only.

The stable signing key is intentionally not stored in Git. The required repository secrets are `WORKOUTAPP_KEYSTORE_BASE64`, `WORKOUTAPP_KEYSTORE_PASSWORD`, `WORKOUTAPP_KEY_ALIAS`, and `WORKOUTAPP_KEY_PASSWORD`. Keep an offline backup of the keystore; losing it would prevent future signed updates.

APKs from GitHub Actions runs before stable signing used ephemeral runner keys and cannot be updated by the new key. The first stable-signed APK therefore requires a one-time uninstall/reinstall; later push artifacts will update normally.

## Evidence boundaries

The repository currently verifies business behavior, real SQLite persistence/restart, the packaged headless process, and APK compilation. It does not claim emulator/instrumentation coverage or physical-device installation until an Android runtime is available for that check.
