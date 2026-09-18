# Headless agent client

WorkoutApp exposes the same application core used by the Android UI through a line-oriented JSON client. Each input line produces one JSON response line on stdout, so an agent can drive the app without a device, emulator, screenshots, or accessibility tooling.

## Build and run

From the repository root:

```powershell
./gradlew.bat :workout-agent:installDist --no-daemon
Get-Content .\commands.jsonl | .\workout-agent\build\install\workout-agent\bin\workout-agent.bat --jsonl --db .\agent-data\workout.db
```

On macOS or Linux, use the generated `bin/workout-agent` launcher instead of the `.bat` launcher.

The database path is explicit and isolated from the Android app. The agent can keep a long-running process open and send commands incrementally, or start a new process against the same database to verify restart and recovery behavior.

## Command shape

The supported operations are:

```text
create_exercise, update_exercise, archive_exercise, list_exercises
create_workout, update_workout, archive_workout, list_workouts
add_exercise, remove_exercise, reorder_exercises, schedule_workout
today, start_session, get_session, mark_exercise, set_session_note
finish_session, cancel_session, history, state
```

`start_session` accepts an optional `substitutions` object whose keys are planned exercise IDs and whose values are performed exercise IDs. These substitutions are validated and snapshotted when the session starts; they never modify the workout template.

Example sequence:

```jsonl
{"op":"create_exercise","name":"Bench Press","sets":3,"reps":5,"weightLb":100,"increaseLb":5,"decreaseLb":10}
{"op":"create_workout","name":"Push Day"}
{"op":"state"}
```

The first two responses contain generated IDs. Agents should read those IDs and use them in later commands rather than assuming stable identifiers.

Successful responses have this shape:

```json
{"ok":true,"op":"state","result":{...}}
```

Validation and domain failures are structured too:

```json
{"ok":false,"code":"incomplete_session","error":"Every exercise must have an outcome before finishing"}
```

## Useful agent workflow

1. Create or inspect exercises.
2. Create a workout and add exercises in the intended order.
3. Assign one or more weekday numbers (`1` through `7`) or names (`MONDAY` through `SUNDAY`).
4. Start a session with an explicit date and optional pre-start substitutions.
5. Mark every exercise as `COMPLETED`, `TOO_HARD`, or `NOT_COMPLETED`, with optional notes.
6. Finish the session, optionally providing the final workout note in the finish_session command.
7. Or cancel the session; cancellation applies no progression and is omitted from completed history.
8. Inspect state, history, or get_session to verify snapshots and progression.

The shared core rejects plan edits while any session is active, requires an outcome for every exercise, clamps weight at zero, and makes repeated `finish_session` calls safe.

## Repeatable smoke check

After packaging the distribution, the repository includes a Windows smoke script:

```powershell
./scripts/headless-smoke.ps1
```

It launches the actual generated process, drives commands through stdin, verifies structured responses and progression, and cleans up its temporary database.
