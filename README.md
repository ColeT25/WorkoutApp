# WorkoutApp

WorkoutApp is a local-only Android workout planner with a shared Kotlin application core and a headless JSONL client. Users create exercises, compose reusable workouts, schedule them on weekdays, log rep- or time-targeted sessions, make temporary substitutions or additions, and apply simple progressive overload.

## Project layout

- `core` — domain models, validation, session rules, progression, and use cases.
- `storage` — shared SQLite schema, repository mapping, and SQL boundary.
- `app` — Android/Compose UI and Android SQLite wiring.
- `workout-agent` — JSONL stdin/stdout client and JDBC SQLite wiring.
- `docs` — product vision, architecture, mockups, headless usage, and verification notes.

## Build

```powershell
./gradlew.bat :core:test :workout-agent:test --no-daemon
./gradlew.bat :app:assembleDebug --no-daemon
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [docs/build-and-verify.md](docs/build-and-verify.md) and [docs/headless-agent.md](docs/headless-agent.md) for installation and agent workflows.
