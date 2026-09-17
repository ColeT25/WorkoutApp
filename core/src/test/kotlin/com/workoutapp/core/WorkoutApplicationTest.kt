package com.workoutapp.core

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class TestIds : IdGenerator {
    private var next = 0
    override fun nextId(): String = "id-${next++}"
}

private class TestClock : WorkoutClock {
    override fun now(): String = "2026-09-17T12:00:00Z"
    override fun today(): String = "2026-09-17"
}

private class InMemoryWorkoutRepository : WorkoutRepository {
    val exercises = linkedMapOf<String, Exercise>()
    val workouts = linkedMapOf<String, Workout>()
    val sessions = linkedMapOf<String, WorkoutSession>()

    override fun <T> transaction(block: () -> T): T = block()
    override fun getExercise(id: String): Exercise? = exercises[id]
    override fun listExercises(includeArchived: Boolean): List<Exercise> = exercises.values.filter { includeArchived || !it.archived }
    override fun insertExercise(exercise: Exercise) { exercises[exercise.id] = exercise }
    override fun updateExercise(exercise: Exercise) { exercises[exercise.id] = exercise }
    override fun getWorkout(id: String): Workout? = workouts[id]
    override fun listWorkouts(includeArchived: Boolean): List<Workout> = workouts.values.filter { includeArchived || !it.archived }
    override fun insertWorkout(workout: Workout) { workouts[workout.id] = workout }
    override fun updateWorkout(workout: Workout) { workouts[workout.id] = workout }
    override fun updateWorkoutExercises(workoutId: String, exercises: List<WorkoutExerciseRef>) { workouts[workoutId] = workouts.getValue(workoutId).copy(exercises = exercises) }
    override fun updateWorkoutSchedule(workoutId: String, days: Set<DayOfWeek>) { workouts[workoutId] = workouts.getValue(workoutId).copy(scheduledDays = days) }
    override fun insertSession(session: WorkoutSession) { sessions[session.id] = session }
    override fun getSession(id: String): WorkoutSession? = sessions[id]
    override fun updateSession(session: WorkoutSession) { sessions[session.id] = session }
    override fun listSessions(): List<WorkoutSession> = sessions.values.toList()
}

class WorkoutApplicationTest {
    private val repository = InMemoryWorkoutRepository()
    private val app = WorkoutApplication(repository, TestClock(), TestIds())

    @Test
    fun `completed session increases shared exercise once`() {
        val exercise = app.createExercise("Bench Press", 3, 8, 135.0, 5.0, 5.0)
        val workout = app.createWorkout("Upper Body")
        app.addExerciseToWorkout(workout.id, exercise.id)
        app.assignWorkoutToDays(workout.id, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        val session = app.startSession(workout.id)
        app.recordExerciseOutcome(session.id, exercise.id, ExerciseOutcome.COMPLETED)
        app.finishSession(session.id)
        app.finishSession(session.id)

        assertEquals(140.0, repository.exercises.getValue(exercise.id).currentWeightLb)
        assertEquals(1, app.getTodayWorkouts(DayOfWeek.THURSDAY).size)
    }

    @Test
    fun `too hard decreases weight and not completed leaves it unchanged`() {
        val exercise = app.createExercise("Squat", 3, 5, 100.0, 5.0, 7.5)
        val workout = app.createWorkout("Lower Body")
        app.addExerciseToWorkout(workout.id, exercise.id)
        val hardSession = app.startSession(workout.id)
        app.recordExerciseOutcome(hardSession.id, exercise.id, ExerciseOutcome.TOO_HARD)
        app.finishSession(hardSession.id)
        assertEquals(92.5, repository.exercises.getValue(exercise.id).currentWeightLb)

        val skippedSession = app.startSession(workout.id)
        app.recordExerciseOutcome(skippedSession.id, exercise.id, ExerciseOutcome.NOT_COMPLETED)
        app.finishSession(skippedSession.id)
        assertEquals(92.5, repository.exercises.getValue(exercise.id).currentWeightLb)
    }

    @Test
    fun `substitution adjusts substitute and preserves planned exercise`() {
        val planned = app.createExercise("Barbell Row", 3, 8, 100.0, 5.0, 5.0)
        val substitute = app.createExercise("Cable Row", 3, 10, 60.0, 2.5, 2.5)
        val workout = app.createWorkout("Back")
        app.addExerciseToWorkout(workout.id, planned.id)
        val session = app.startSession(workout.id, substitutions = mapOf(planned.id to substitute.id))
        app.recordExerciseOutcome(session.id, planned.id, ExerciseOutcome.COMPLETED)
        app.finishSession(session.id)

        assertEquals(100.0, repository.exercises.getValue(planned.id).currentWeightLb)
        assertEquals(62.5, repository.exercises.getValue(substitute.id).currentWeightLb)
        assertEquals(substitute.id, app.history().single().exercises.single().performedExerciseId)
        assertEquals(planned.id, repository.workouts.getValue(workout.id).exercises.single().exerciseId)
    }

    @Test
    fun `weight cannot go below zero and all outcomes are required`() {
        val exercise = app.createExercise("Deadlift", 1, 1, 2.0, 0.0, 10.0)
        val workout = app.createWorkout("Test")
        app.addExerciseToWorkout(workout.id, exercise.id)
        val incomplete = app.startSession(workout.id)
        assertFailsWith<WorkoutException> { app.finishSession(incomplete.id) }
        app.recordExerciseOutcome(incomplete.id, exercise.id, ExerciseOutcome.TOO_HARD)
        app.finishSession(incomplete.id)
        assertEquals(0.0, repository.exercises.getValue(exercise.id).currentWeightLb)
    }

    @Test
    fun `plan edits are blocked during active session and duplicate exercises are rejected`() {
        val exercise = app.createExercise("Press", 3, 5, 50.0, 0.0, 0.0)
        val workout = app.createWorkout("Push")
        app.addExerciseToWorkout(workout.id, exercise.id)
        assertFailsWith<WorkoutException> { app.addExerciseToWorkout(workout.id, exercise.id) }
        val session = app.startSession(workout.id)
        assertTrue(session.status == SessionStatus.ACTIVE)
        assertFailsWith<WorkoutException> { app.updateWorkout(workout.id, "Renamed") }
    }

    @Test
    fun `substitution must target this workout and cannot duplicate a performed exercise`() {
        val first = app.createExercise("First", 3, 5, 50.0, 0.0, 0.0)
        val second = app.createExercise("Second", 3, 5, 60.0, 0.0, 0.0)
        val substitute = app.createExercise("Substitute", 3, 5, 70.0, 0.0, 0.0)
        val workout = app.createWorkout("Two exercises")
        app.addExerciseToWorkout(workout.id, first.id)
        app.addExerciseToWorkout(workout.id, second.id)

        assertFailsWith<WorkoutException> {
            app.startSession(workout.id, substitutions = mapOf("not-in-workout" to substitute.id))
        }
        assertFailsWith<WorkoutException> {
            app.startSession(workout.id, substitutions = mapOf(first.id to substitute.id, second.id to substitute.id))
        }
    }

    @Test
    fun `referenced exercise cannot be archived until its active workout is archived`() {
        val exercise = app.createExercise("Referenced", 3, 5, 50.0, 0.0, 0.0)
        val workout = app.createWorkout("Reference")
        app.addExerciseToWorkout(workout.id, exercise.id)

        assertFailsWith<WorkoutException> { app.archiveExercise(exercise.id) }
        app.archiveWorkout(workout.id)
        assertTrue(app.archiveExercise(exercise.id).archived)
    }
}
