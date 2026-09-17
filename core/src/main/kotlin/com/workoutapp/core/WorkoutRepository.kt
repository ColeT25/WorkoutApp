package com.workoutapp.core

import java.time.DayOfWeek

interface WorkoutRepository {
    fun <T> transaction(block: () -> T): T

    fun getExercise(id: String): Exercise?

    fun listExercises(includeArchived: Boolean = false): List<Exercise>

    fun insertExercise(exercise: Exercise)

    fun updateExercise(exercise: Exercise)

    fun getWorkout(id: String): Workout?

    fun listWorkouts(includeArchived: Boolean = false): List<Workout>

    fun insertWorkout(workout: Workout)

    fun updateWorkout(workout: Workout)

    fun updateWorkoutExercises(workoutId: String, exercises: List<WorkoutExerciseRef>)

    fun updateWorkoutSchedule(workoutId: String, days: Set<DayOfWeek>)

    fun insertSession(session: WorkoutSession)

    fun getSession(id: String): WorkoutSession?

    fun updateSession(session: WorkoutSession)

    fun listSessions(): List<WorkoutSession>
}
