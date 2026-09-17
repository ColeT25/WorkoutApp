package com.workoutapp.core

import java.time.DayOfWeek

class WorkoutException(
    val code: String,
    override val message: String,
) : IllegalArgumentException(message)

class WorkoutApplication(
    private val repository: WorkoutRepository,
    private val clock: WorkoutClock = SystemWorkoutClock(),
    private val ids: IdGenerator = UuidIdGenerator(),
) {
    fun createExercise(
        name: String,
        setCount: Int,
        repCount: Int,
        currentWeightLb: Double,
        increaseLb: Double,
        decreaseLb: Double,
    ): Exercise {
        ensureNoActiveSession()
        validateExerciseValues(name, setCount, repCount, currentWeightLb, increaseLb, decreaseLb)
        if (repository.listExercises(includeArchived = true).any { it.name.equals(name.trim(), ignoreCase = true) }) {
            throw WorkoutException("duplicate_exercise", "An exercise with this name already exists")
        }
        return Exercise(
            id = ids.nextId(),
            name = name.trim(),
            setCount = setCount,
            repCount = repCount,
            currentWeightLb = currentWeightLb,
            increaseLb = increaseLb,
            decreaseLb = decreaseLb,
        ).also(repository::insertExercise)
    }

    fun updateExercise(
        id: String,
        name: String,
        setCount: Int,
        repCount: Int,
        currentWeightLb: Double,
        increaseLb: Double,
        decreaseLb: Double,
    ): Exercise {
        ensureNoActiveSession()
        val existing = requireExercise(id)
        validateExerciseValues(name, setCount, repCount, currentWeightLb, increaseLb, decreaseLb)
        if (repository.listExercises(includeArchived = true).any {
                it.id != id && it.name.equals(name.trim(), ignoreCase = true)
            }
        ) {
            throw WorkoutException("duplicate_exercise", "An exercise with this name already exists")
        }
        return existing.copy(
            name = name.trim(),
            setCount = setCount,
            repCount = repCount,
            currentWeightLb = currentWeightLb,
            increaseLb = increaseLb,
            decreaseLb = decreaseLb,
        ).also(repository::updateExercise)
    }

    fun archiveExercise(id: String): Exercise {
        ensureNoActiveSession()
        val exercise = requireExercise(id)
        if (repository.listWorkouts().any { workout -> workout.exercises.any { it.exerciseId == id } }) {
            throw WorkoutException("exercise_in_use", "Remove the exercise from active workouts before archiving it")
        }
        return exercise.copy(archived = true).also(repository::updateExercise)
    }

    fun createWorkout(name: String): Workout {
        ensureNoActiveSession()
        if (name.isBlank()) throw WorkoutException("invalid_workout", "Workout name is required")
        if (repository.listWorkouts(includeArchived = true).any { it.name.equals(name.trim(), ignoreCase = true) }) {
            throw WorkoutException("duplicate_workout", "A workout with this name already exists")
        }
        return Workout(id = ids.nextId(), name = name.trim()).also(repository::insertWorkout)
    }

    fun updateWorkout(id: String, name: String): Workout {
        ensureNoActiveSession()
        val existing = requireWorkout(id)
        if (name.isBlank()) throw WorkoutException("invalid_workout", "Workout name is required")
        if (repository.listWorkouts(includeArchived = true).any {
                it.id != id && it.name.equals(name.trim(), ignoreCase = true)
            }
        ) {
            throw WorkoutException("duplicate_workout", "A workout with this name already exists")
        }
        return existing.copy(name = name.trim()).also(repository::updateWorkout)
    }

    fun archiveWorkout(id: String): Workout {
        ensureNoActiveSession()
        return requireWorkout(id).copy(archived = true).also(repository::updateWorkout)
    }

    fun addExerciseToWorkout(workoutId: String, exerciseId: String): Workout {
        ensureNoActiveSession()
        val workout = requireWorkout(workoutId)
        val exercise = requireExercise(exerciseId)
        if (exercise.archived) throw WorkoutException("archived_exercise", "Archived exercises cannot be added")
        if (workout.exercises.any { it.exerciseId == exerciseId }) {
            throw WorkoutException("duplicate_workout_exercise", "The exercise is already in this workout")
        }
        val updated = workout.copy(
            exercises = workout.exercises + WorkoutExerciseRef(exerciseId, workout.exercises.size),
        )
        repository.updateWorkoutExercises(workoutId, updated.exercises)
        return updated
    }

    fun removeExerciseFromWorkout(workoutId: String, exerciseId: String): Workout {
        ensureNoActiveSession()
        val workout = requireWorkout(workoutId)
        val updated = workout.copy(
            exercises = workout.exercises.filterNot { it.exerciseId == exerciseId }
                .mapIndexed { index, ref -> ref.copy(position = index) },
        )
        repository.updateWorkoutExercises(workoutId, updated.exercises)
        return updated
    }

    fun reorderWorkoutExercises(workoutId: String, exerciseIds: List<String>): Workout {
        ensureNoActiveSession()
        val workout = requireWorkout(workoutId)
        val expected = workout.exercises.map { it.exerciseId }.toSet()
        if (exerciseIds.size != expected.size || exerciseIds.toSet() != expected) {
            throw WorkoutException("invalid_order", "The reordered list must contain each workout exercise exactly once")
        }
        val updated = workout.copy(
            exercises = exerciseIds.mapIndexed { index, id -> WorkoutExerciseRef(id, index) },
        )
        repository.updateWorkoutExercises(workoutId, updated.exercises)
        return updated
    }

    fun assignWorkoutToDays(workoutId: String, days: Set<DayOfWeek>): Workout {
        ensureNoActiveSession()
        val workout = requireWorkout(workoutId)
        val updated = workout.copy(scheduledDays = days)
        repository.updateWorkoutSchedule(workoutId, days)
        return updated
    }

    fun startSession(
        workoutId: String,
        localDate: String = clock.today(),
        substitutions: Map<String, String> = emptyMap(),
    ): WorkoutSession {
        ensureNoActiveSession()
        val workout = requireWorkout(workoutId)
        if (workout.archived) throw WorkoutException("archived_workout", "Archived workouts cannot be started")
        if (workout.exercises.isEmpty()) throw WorkoutException("empty_workout", "A workout must contain at least one exercise")
        val plannedIds = workout.exercises.map { it.exerciseId }.toSet()
        if (substitutions.keys.any { it !in plannedIds }) {
            throw WorkoutException("invalid_substitution", "Substitutions must refer to exercises in this workout")
        }
        val performedIds = workout.exercises.map { it.exerciseId }.map { substitutions[it] ?: it }
        if (performedIds.size != performedIds.toSet().size) {
            throw WorkoutException("duplicate_substitution", "An exercise cannot be performed more than once in a workout")
        }
        val sessionExercises = workout.exercises.sortedBy { it.position }.mapIndexed { index, ref ->
            val planned = requireExercise(ref.exerciseId)
            if (planned.archived) {
                throw WorkoutException("archived_exercise", "Workout contains an archived exercise")
            }
            val performed = substitutions[planned.id]?.let(::requireExercise) ?: planned
            if (performed.archived) {
                throw WorkoutException("archived_exercise", "Archived exercises cannot be substituted")
            }
            SessionExercise(
                id = ids.nextId(),
                position = index,
                plannedExerciseId = planned.id,
                plannedExerciseName = planned.name,
                plannedSetCount = planned.setCount,
                plannedRepCount = planned.repCount,
                plannedWeightLb = planned.currentWeightLb,
                performedExerciseId = performed.id,
                performedExerciseName = performed.name,
                performedSetCount = performed.setCount,
                performedRepCount = performed.repCount,
                performedWeightLb = performed.currentWeightLb,
                performedIncreaseLb = performed.increaseLb,
                performedDecreaseLb = performed.decreaseLb,
            )
        }
        return WorkoutSession(
            id = ids.nextId(),
            workoutId = workout.id,
            workoutName = workout.name,
            localDate = localDate,
            startedAt = clock.now(),
            exercises = sessionExercises,
        ).also(repository::insertSession)
    }

    fun recordExerciseOutcome(
        sessionId: String,
        plannedExerciseId: String,
        outcome: ExerciseOutcome,
        note: String = "",
    ): WorkoutSession {
        val session = requireActiveSession(sessionId)
        if (session.exercises.none { it.plannedExerciseId == plannedExerciseId }) {
            throw WorkoutException("missing_session_exercise", "The exercise is not in this session")
        }
        val updated = session.copy(
            exercises = session.exercises.map { entry ->
                if (entry.plannedExerciseId == plannedExerciseId) entry.copy(outcome = outcome, note = note)
                else entry
            },
        )
        repository.updateSession(updated)
        return updated
    }

    fun setSessionNote(sessionId: String, note: String): WorkoutSession {
        val session = requireActiveSession(sessionId)
        return session.copy(note = note).also(repository::updateSession)
    }

    fun finishSession(sessionId: String): WorkoutSession = repository.transaction {
        val session = repository.getSession(sessionId)
            ?: throw WorkoutException("missing_session", "Session not found")
        if (session.status == SessionStatus.COMPLETED) return@transaction session
        val incomplete = session.exercises.filter { it.outcome == null }
        if (incomplete.isNotEmpty()) {
            throw WorkoutException("incomplete_session", "Every exercise must have an outcome before finishing")
        }
        session.exercises.forEach { entry ->
            val exercise = requireExercise(entry.performedExerciseId)
            val adjustment = when (entry.outcome) {
                ExerciseOutcome.COMPLETED -> entry.performedIncreaseLb
                ExerciseOutcome.TOO_HARD -> -entry.performedDecreaseLb
                ExerciseOutcome.NOT_COMPLETED, null -> 0.0
            }
            repository.updateExercise(exercise.copy(currentWeightLb = (exercise.currentWeightLb + adjustment).coerceAtLeast(0.0)))
        }
        val completed = session.copy(status = SessionStatus.COMPLETED, completedAt = clock.now())
        repository.updateSession(completed)
        completed
    }

    fun getTodayWorkouts(day: DayOfWeek): List<Workout> = repository.listWorkouts()
        .filter { day in it.scheduledDays }

    fun listExercises(includeArchived: Boolean = false): List<Exercise> = repository.listExercises(includeArchived)

    fun listWorkouts(includeArchived: Boolean = false): List<Workout> = repository.listWorkouts(includeArchived)

    fun getSession(id: String): WorkoutSession? = repository.getSession(id)

    fun activeSession(): WorkoutSession? = repository.listSessions()
        .firstOrNull { it.status == SessionStatus.ACTIVE }

    fun history(): List<WorkoutSession> = repository.listSessions()
        .filter { it.status == SessionStatus.COMPLETED }
        .sortedWith(compareByDescending<WorkoutSession> { it.localDate }.thenByDescending { it.startedAt })

    private fun validateExerciseValues(
        name: String,
        setCount: Int,
        repCount: Int,
        currentWeightLb: Double,
        increaseLb: Double,
        decreaseLb: Double,
    ) {
        if (name.isBlank()) throw WorkoutException("invalid_exercise", "Exercise name is required")
        if (setCount < 1) throw WorkoutException("invalid_exercise", "Set count must be at least 1")
        if (repCount < 1) throw WorkoutException("invalid_exercise", "Rep count must be at least 1")
        if (currentWeightLb < 0 || increaseLb < 0 || decreaseLb < 0) {
            throw WorkoutException("invalid_exercise", "Weights and progression amounts cannot be negative")
        }
    }

    private fun ensureNoActiveSession() {
        if (repository.listSessions().any { it.status == SessionStatus.ACTIVE }) {
            throw WorkoutException("active_session", "Plan editing is disabled during an active workout")
        }
    }

    private fun requireExercise(id: String): Exercise = repository.getExercise(id)
        ?: throw WorkoutException("missing_exercise", "Exercise not found: $id")

    private fun requireWorkout(id: String): Workout = repository.getWorkout(id)
        ?: throw WorkoutException("missing_workout", "Workout not found: $id")

    private fun requireActiveSession(id: String): WorkoutSession {
        val session = repository.getSession(id)
            ?: throw WorkoutException("missing_session", "Session not found: $id")
        if (session.status != SessionStatus.ACTIVE) {
            throw WorkoutException("completed_session", "Completed sessions cannot be changed")
        }
        return session
    }
}
