package com.workoutapp.core

import java.time.DayOfWeek

const val DEFAULT_DURATION_SECONDS = 60

enum class ExerciseTargetType {
    REPS,
    TIME,
}

data class Exercise(
    val id: String,
    val name: String,
    val setCount: Int,
    val repCount: Int,
    val currentWeightLb: Double,
    val increaseLb: Double,
    val decreaseLb: Double,
    val targetType: ExerciseTargetType = ExerciseTargetType.REPS,
    val durationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val archived: Boolean = false,
)

data class WorkoutExerciseRef(
    val exerciseId: String,
    val position: Int,
)

data class Workout(
    val id: String,
    val name: String,
    val exercises: List<WorkoutExerciseRef> = emptyList(),
    val scheduledDays: Set<DayOfWeek> = emptySet(),
    val archived: Boolean = false,
)

enum class ExerciseOutcome {
    COMPLETED,
    TOO_HARD,
    NOT_COMPLETED,
}

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

data class SessionExercise(
    val id: String,
    val position: Int,
    val plannedExerciseId: String,
    val plannedExerciseName: String,
    val plannedSetCount: Int,
    val plannedRepCount: Int,
    val plannedWeightLb: Double,
    val performedExerciseId: String,
    val performedExerciseName: String,
    val performedSetCount: Int,
    val performedRepCount: Int,
    val performedWeightLb: Double,
    val performedIncreaseLb: Double,
    val performedDecreaseLb: Double,
    val outcome: ExerciseOutcome? = null,
    val note: String = "",
    val plannedTargetType: ExerciseTargetType = ExerciseTargetType.REPS,
    val plannedDurationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val performedTargetType: ExerciseTargetType = ExerciseTargetType.REPS,
    val performedDurationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val isAdHoc: Boolean = false,
)

data class WorkoutSession(
    val id: String,
    val workoutId: String,
    val workoutName: String,
    val localDate: String,
    val startedAt: String,
    val completedAt: String? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val note: String = "",
    val exercises: List<SessionExercise> = emptyList(),
)

interface IdGenerator {
    fun nextId(): String
}

class UuidIdGenerator : IdGenerator {
    override fun nextId(): String = java.util.UUID.randomUUID().toString()
}

interface WorkoutClock {
    fun now(): String

    fun today(): String
}

class SystemWorkoutClock : WorkoutClock {
    override fun now(): String = java.time.Instant.now().toString()

    override fun today(): String = java.time.LocalDate.now().toString()
}
