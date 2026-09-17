# WorkoutApp Architecture

## Overview

WorkoutApp is a local-only Android application with two first-class clients:

1. A human-facing Android UI.
2. A headless agent-facing CLI or JSONL interface.

Both clients use the same application and business-logic layers. The headless client is not a separate implementation of workout behavior; it is another way to operate the same application core.

```text
Android Compose UI ─┐
                    ├─> Shared application/core layer ─> Shared SQLite data
Headless agent CLI ─┘
```

The initial product has no backend, accounts, cloud synchronization, network dependency, or social features.

## Architectural goals

- Keep workout rules independent of Android UI and framework details.
- Make the application usable by an automated agent without UI interaction.
- Make important behavior observable through structured state and history.
- Use real persistence in headless scenarios rather than relying only on mocks.
- Preserve historical sessions when future plans change.
- Make APK builds and verification repeatable through GitHub Actions.

## Proposed project boundaries

```text
:core
  Domain models
  Application use cases
  Workout-session lifecycle
  Progressive-overload rules
  Validation
  Command and query contracts

:storage
  Shared SQLite schema
  Repository contracts and implementations
  Android database driver
  JVM/headless database driver

:app
  Compose screens
  ViewModels
  Android navigation and start-workout review
  Android application wiring

:workout-agent
  CLI or JSONL command interface
  Scenario execution
  Structured state inspection
```

The exact module layout may be adjusted during scaffolding, but UI code must not own business rules and the headless client must not duplicate them.

## Android client

The Android application will use Kotlin and Jetpack Compose.

The UI flow is:

```text
Compose screen
    ↓
ViewModel
    ↓
Shared application use case
    ↓
Repository
    ↓
SQLite database
```

Primary screens are:

- Today and scheduled workouts.
- Exercise management.
- Workout editing and weekday assignment.
- Active workout sessions.
- Temporary exercise substitution.
- Session history.

Plan and exercise editing is disabled while a workout session is active. If the application is interrupted, the active session remains resumable.

## Headless agent client

The headless client will be a local CLI or JSONL interface rather than an HTTP service. It will call the same application use cases used by the Android UI.

Representative operations include:

```text
create exercise
create workout
add exercise to workout
assign workout to weekday
start session
start session with optional substitutions
mark exercise outcome
finish session
inspect current state
inspect history
```

Responses should be structured and machine-readable, including created identifiers, updated state, and validation errors.

Headless scenarios should support:

- An isolated database directory.
- Deterministic dates and clocks.
- Scripted multi-step workflows.
- State inspection between operations.
- Process restart and recovery checks.

The agent uses its own local database for scenarios. It does not directly manipulate the database on a physical phone.

## Storage architecture

The application uses a shared SQLite-backed data design with an Android `SQLiteOpenHelper` driver and a JVM/headless JDBC SQLite driver. Both implement the same small `SqlExecutor` boundary and share the schema and repository mapping.

The storage boundary should support real repository integration tests and should not force the headless client to depend on Android-only database APIs.

## Core data concepts

### Exercise

An exercise is a reusable shared record containing:

- Name
- Set count
- Rep count
- Current weight in pounds
- Increase amount
- Decrease amount
- Archived state

If the same exercise is used in multiple workouts, all of these settings and its current weight are shared.

### Workout

A workout is a named, ordered group of exercise references. The same exercise may not appear more than once in a workout.

### Schedule

A workout may be assigned to one or more weekdays. Multiple workouts may be scheduled on the same weekday.

### Workout session

A session records one dated execution of a workout. It preserves snapshots of the relevant plan values so later edits do not rewrite history.

Each session exercise records:

- Planned exercise.
- Performed exercise, if substituted.
- Sets, reps, and weight used for the session.
- Outcome: completed, too hard, or not completed.
- Exercise notes.

Sessions may also contain workout-level notes.

## Session and progression rules

When a session is finalized:

- Every planned exercise must have an outcome.
- Completed increases the performed exercise's current weight by its increase amount.
- Too hard decreases the performed exercise's current weight by its decrease amount.
- Not completed leaves the performed exercise's current weight unchanged.
- Weight cannot fall below zero.
- Each exercise receives at most one adjustment for that session.
- Repeating finalization is safe and does not apply progression twice.

Finalization should save the session, its snapshots, and weight adjustments transactionally.

## Temporary substitutions

Substitutions are selected in the start-workout review and passed into session creation. They apply only to that session.

- The underlying workout template is unchanged.
- History records both the planned and performed exercises.
- The performed substitute uses its own settings and current weight.
- Progression applies to the performed substitute.
- The planned exercise is not adjusted because it was not performed.

## History and plan changes

Exercises and workouts should be archived rather than physically deleted when deletion would make history unreadable.

Historical sessions preserve their names, settings, weights, outcomes, substitutions, and notes. Editing or archiving a plan changes future use only.

## Verification architecture

Verification is layered and reports each layer separately.

### Pure business-logic verification

Fast JVM tests cover the progression decision table, validation, substitutions, shared exercise behavior, and idempotent finalization.

### Storage verification

The headless integration tests use the real JDBC/SQLite repository to verify persistence, transactions, snapshots, archives, and recovery.

### Headless scenario verification

The agent interface executes realistic multi-step workflows and inspects state after each operation. Scenarios cover process restart, session recovery, and behavior that would otherwise be tested only through UI interaction.

### Android runtime verification

Android instrumented tests verify Android database/runtime integration on an emulator. A small number of Compose tests verify that screens are wired to the correct use cases.

### Build verification

GitHub Actions should run the fast checks and build an installable APK. The workflow should publish test reports and the APK artifact. Later, signed APKs can be attached to GitHub Releases for durable phone installation.

The verification report must distinguish passing unit tests, storage tests, headless scenarios, emulator tests, build checks, and any unavailable or omitted runtime layer.

## Current implementation decisions

The first implementation uses:

- Kotlin/JVM 17 for the shared modules and Android app.
- Android API 26 minimum, API 36 compile/target configuration.
- `com.workoutapp.android` as the Android application ID.
- JSONL over stdin/stdout as the headless interface, with an installable CLI distribution.
- SQLite through Android `SQLiteOpenHelper` and headless JDBC SQLite drivers behind the shared `SqlExecutor` boundary.

These choices keep the local MVP small while leaving room for a richer agent protocol or a different UI layer later.
