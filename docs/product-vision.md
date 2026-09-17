# WorkoutApp Product Vision

## Purpose

WorkoutApp is a simple, flexible, local-only Android app for planning workouts, logging sessions, and managing straightforward progressive overload.

The app should make it easy to define reusable exercises, compose them into workouts, assign workouts to recurring days, and preserve an accurate history of what was performed.

## MVP scope

### Exercises

Users create and manage their own exercises. The app does not need a built-in exercise catalog.

Each exercise contains:

- Name
- Set count
- Rep count
- Current weight
- Progressive overload increase amount
- Progressive overload decrease amount

Exercise settings are shared wherever the same exercise is reused. If an exercise appears in multiple workouts, changing its settings or current weight affects all of those workouts.

The initial version only needs to support weight-and-repetition exercises using pounds.

### Workouts

A workout is a named, ordered group of exercises.

Users can:

- Create, edit, and delete workouts.
- Add existing exercises to workouts.
- Remove exercises from workouts.
- Reorder exercises within a workout.
- Assign a workout to one or more days of the week.

The same workout can be scheduled on multiple days.

### Workout sessions

Starting a scheduled workout creates a workout session for that date. The session records the exercise results and notes without changing the historical record of earlier sessions.

For each exercise in a session, the user can:

- Mark it completed.
- Mark it not completed.
- Mark it too hard.
- Record notes for that exercise session.

The user can also record notes for the workout session as a whole.

The app should track which workouts were completed on each calendar day and provide a history of past sessions.

## Progressive overload rules

Each exercise is adjusted at most once per workout session:

- Completed: increase current weight by the exercise's increase amount.
- Too hard: decrease current weight by the exercise's decrease amount.
- Not completed: leave current weight unchanged.

The increase and decrease amounts are independent settings. Either amount may be zero. Weight must not be reduced below zero.

The adjustment should happen when the session is saved or completed, not once per set.

## Temporary substitutions

Before starting a workout, a user can substitute an exercise with another existing exercise.

Substitution behavior:

- The substitution applies only to the current workout session.
- The underlying workout template is not changed.
- The session history records the planned exercise and the exercise actually performed.
- The performed substitute receives the session result and any applicable weight adjustment.
- The original planned exercise is not adjusted because it was not performed.

## History and data integrity

Past workout sessions should remain accurate even when exercises or workout templates are later edited or deleted.

Session history should preserve the relevant values from the time of the session, including:

- Date
- Workout name
- Planned exercise
- Performed exercise, if different
- Sets and reps used
- Weight used
- Result status
- Exercise notes
- Workout notes

Editing a reusable exercise or workout changes future use, not past sessions.

## Product principles

- Keep the core workflow fast and uncomplicated.
- Prefer user-created content over a prebuilt fitness taxonomy.
- Preserve history rather than rewriting it when plans change.
- Make progressive overload predictable and easy to override through exercise settings.
- Keep all data local to the app for the initial version.

## Explicit non-goals for the initial version

The first version does not need:

- Built-in exercises
- Muscle groups
- Equipment metadata
- Exercise instructions or aliases
- Units other than pounds
- Data export or cloud sync
- Warm-up sets
- RPE or RIR tracking
- Time, distance, or cardio tracking
- Percentage-based programming or one-rep-max calculations
- Wearable integrations
- Social features

## Suggested primary screens

1. Today: scheduled workouts and recent completion history.
2. Workout editor: create workouts, add/reorder exercises, and assign weekdays.
3. Exercise editor: manage exercise settings and current weight.
4. Active workout: complete exercises, substitute exercises, and add notes.
5. History: browse completed workouts by date and inspect session details.
