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
    fun `exercise edits are allowed during active session but snapshots stay stable`() {
        val exercise = app.createExercise("Press", 3, 5, 50.0, 5.0, 2.5)
        val workout = app.createWorkout("Push")
        app.addExerciseToWorkout(workout.id, exercise.id)
        assertFailsWith<WorkoutException> { app.addExerciseToWorkout(workout.id, exercise.id) }
        val session = app.startSession(workout.id)
        assertTrue(session.status == SessionStatus.ACTIVE)
        assertFailsWith<WorkoutException> { app.updateWorkout(workout.id, "Renamed") }
        app.updateExercise(exercise.id, "Press edited", 4, 10, 55.0, 9.0, 8.0)

        val snapshot = app.getSession(session.id)!!.exercises.single()
        assertEquals("Press", snapshot.performedExerciseName)
        assertEquals(3, snapshot.performedSetCount)
        assertEquals(5, snapshot.performedRepCount)
        assertEquals(50.0, snapshot.performedWeightLb)
        assertEquals(5.0, snapshot.performedIncreaseLb)
        app.recordExerciseOutcome(session.id, exercise.id, ExerciseOutcome.COMPLETED)
        val completed = app.finishSession(session.id, "Saved at finish")
        assertEquals("Saved at finish", completed.note)
        assertEquals(60.0, repository.exercises.getValue(exercise.id).currentWeightLb)
    }

    @Test
    fun `cancelling active session applies no progression and clears active state`() {
        val exercise = app.createExercise("Cancel Press", 3, 5, 100.0, 5.0, 5.0)
        val workout = app.createWorkout("Cancel Test")
        app.addExerciseToWorkout(workout.id, exercise.id)
        val session = app.startSession(workout.id)

        val cancelled = app.cancelSession(session.id)

        assertEquals(SessionStatus.CANCELLED, cancelled.status)
        assertEquals(null, app.activeSession())
        assertTrue(app.history().isEmpty())
        assertEquals(100.0, repository.exercises.getValue(exercise.id).currentWeightLb)
        assertEquals(SessionStatus.CANCELLED, app.cancelSession(session.id).status)
        app.updateWorkout(workout.id, "Renamed after cancel")
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
    fun `time target is snapshotted and completed progression still changes weight`() {
        val exercise = app.createExercise(
            name = "Plank",
            setCount = 3,
            repCount = 1,
            currentWeightLb = 0.0,
            increaseLb = 0.0,
            decreaseLb = 0.0,
            targetType = ExerciseTargetType.TIME,
            durationSeconds = 45,
        )
        val workout = app.createWorkout("Core")
        app.addExerciseToWorkout(workout.id, exercise.id)

        val session = app.startSession(workout.id)
        val entry = session.exercises.single()
        assertEquals(ExerciseTargetType.TIME, entry.performedTargetType)
        assertEquals(45, entry.performedDurationSeconds)
        app.updateExercise(exercise.id, "Plank", 4, 1, 0.0, 0.0, 0.0, ExerciseTargetType.TIME, 60)
        assertEquals(45, app.getSession(session.id)!!.exercises.single().performedDurationSeconds)

        app.recordExerciseOutcome(session.id, exercise.id, ExerciseOutcome.COMPLETED)
        app.finishSession(session.id)
        assertEquals(0.0, repository.exercises.getValue(exercise.id).currentWeightLb)
    }

    @Test
    fun `session-only additions work before and during a workout and can be removed`() {
        val planned = app.createExercise("Planned", 3, 5, 50.0, 5.0, 5.0)
        val preStart = app.createExercise("Pre-start add", 2, 10, 20.0, 2.0, 2.0)
        val during = app.createExercise("During add", 4, 8, 30.0, 3.0, 3.0)
        val workout = app.createWorkout("Flexible")
        app.addExerciseToWorkout(workout.id, planned.id)

        val session = app.startSession(workout.id, additionalExerciseIds = listOf(preStart.id))
        assertEquals(2, session.exercises.size)
        assertTrue(session.exercises.last().isAdHoc)
        assertEquals(1, session.exercises.last().position)
        assertFailsWith<WorkoutException> { app.startSession(workout.id, additionalExerciseIds = listOf(planned.id)) }

        val withDuring = app.addExerciseToSession(session.id, during.id)
        assertEquals(3, withDuring.exercises.size)
        assertTrue(withDuring.exercises.last().isAdHoc)
        assertFailsWith<WorkoutException> { app.addExerciseToSession(session.id, during.id) }
        app.updateExercise(during.id, "During add edited", 1, 1, 99.0, 9.0, 9.0, ExerciseTargetType.TIME, 90)
        val duringSnapshot = app.getSession(session.id)!!.exercises.last()
        assertEquals("During add", duringSnapshot.performedExerciseName)
        assertEquals(30.0, duringSnapshot.performedWeightLb)
        assertEquals(ExerciseTargetType.REPS, duringSnapshot.performedTargetType)
        assertEquals(8, duringSnapshot.performedRepCount)
        val afterRemove = app.removeExerciseFromSession(session.id, withDuring.exercises.last().id)
        assertEquals(2, afterRemove.exercises.size)
        assertFailsWith<WorkoutException> { app.removeExerciseFromSession(session.id, afterRemove.exercises.first().id) }

        afterRemove.exercises.forEach { app.recordExerciseOutcome(session.id, it.plannedExerciseId, ExerciseOutcome.COMPLETED) }
        val completed = app.finishSession(session.id)
        assertEquals(2, completed.exercises.size)
        assertEquals(55.0, repository.exercises.getValue(planned.id).currentWeightLb)
        assertEquals(22.0, repository.exercises.getValue(preStart.id).currentWeightLb)
        assertEquals(99.0, repository.exercises.getValue(during.id).currentWeightLb)
        assertTrue(app.history().single().exercises.all { it.isAdHoc || it.performedExerciseId == planned.id })
    }

    @Test
    fun `invalid time duration and duplicate active additions are rejected`() {
        assertFailsWith<WorkoutException> {
            app.createExercise("Invalid time", 1, 1, 0.0, 0.0, 0.0, ExerciseTargetType.TIME, 0)
        }
        val planned = app.createExercise("Planned", 1, 1, 0.0, 0.0, 0.0)
        val added = app.createExercise("Added", 1, 1, 0.0, 0.0, 0.0)
        val workout = app.createWorkout("Duplicate")
        app.addExerciseToWorkout(workout.id, planned.id)
        val session = app.startSession(workout.id)
        app.addExerciseToSession(session.id, added.id)
        assertFailsWith<WorkoutException> { app.addExerciseToSession(session.id, added.id) }
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
