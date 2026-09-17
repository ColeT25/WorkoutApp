package com.workoutapp.storage

import com.workoutapp.core.Exercise
import com.workoutapp.core.ExerciseOutcome
import com.workoutapp.core.SessionExercise
import com.workoutapp.core.SessionStatus
import com.workoutapp.core.Workout
import com.workoutapp.core.WorkoutExerciseRef
import com.workoutapp.core.WorkoutRepository
import com.workoutapp.core.WorkoutSession
import java.time.DayOfWeek

class SqlWorkoutRepository(
    private val sql: SqlExecutor,
) : WorkoutRepository {
    init {
        DatabaseSchema.statements.forEach(sql::execute)
    }

    override fun <T> transaction(block: () -> T): T = sql.transaction(block)

    override fun getExercise(id: String): Exercise? = sql.query(
        "SELECT * FROM exercises WHERE id = ?",
        listOf(id),
    ).firstOrNull()?.toExercise()

    override fun listExercises(includeArchived: Boolean): List<Exercise> = sql.query(
        if (includeArchived) "SELECT * FROM exercises ORDER BY name COLLATE NOCASE" else "SELECT * FROM exercises WHERE archived = 0 ORDER BY name COLLATE NOCASE",
    ).map { row -> row.toExercise() }

    override fun insertExercise(exercise: Exercise) {
        sql.execute(
            "INSERT INTO exercises (id, name, set_count, rep_count, current_weight_lb, increase_lb, decrease_lb, archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(exercise.id, exercise.name, exercise.setCount, exercise.repCount, exercise.currentWeightLb, exercise.increaseLb, exercise.decreaseLb, exercise.archived.asInt()),
        )
    }

    override fun updateExercise(exercise: Exercise) {
        sql.execute(
            "UPDATE exercises SET name = ?, set_count = ?, rep_count = ?, current_weight_lb = ?, increase_lb = ?, decrease_lb = ?, archived = ? WHERE id = ?",
            listOf(exercise.name, exercise.setCount, exercise.repCount, exercise.currentWeightLb, exercise.increaseLb, exercise.decreaseLb, exercise.archived.asInt(), exercise.id),
        )
    }

    override fun getWorkout(id: String): Workout? = sql.query(
        "SELECT * FROM workouts WHERE id = ?",
        listOf(id),
    ).firstOrNull()?.toWorkout()

    override fun listWorkouts(includeArchived: Boolean): List<Workout> = sql.query(
        if (includeArchived) "SELECT * FROM workouts ORDER BY name COLLATE NOCASE" else "SELECT * FROM workouts WHERE archived = 0 ORDER BY name COLLATE NOCASE",
    ).map { row -> row.toWorkout() }

    override fun insertWorkout(workout: Workout) {
        sql.execute(
            "INSERT INTO workouts (id, name, archived) VALUES (?, ?, ?)",
            listOf(workout.id, workout.name, workout.archived.asInt()),
        )
        updateWorkoutExercises(workout.id, workout.exercises)
        updateWorkoutSchedule(workout.id, workout.scheduledDays)
    }

    override fun updateWorkout(workout: Workout) {
        sql.execute(
            "UPDATE workouts SET name = ?, archived = ? WHERE id = ?",
            listOf(workout.name, workout.archived.asInt(), workout.id),
        )
    }

    override fun updateWorkoutExercises(workoutId: String, exercises: List<WorkoutExerciseRef>) = transaction {
        sql.execute("DELETE FROM workout_exercises WHERE workout_id = ?", listOf(workoutId))
        exercises.sortedBy { it.position }.forEach { ref ->
            sql.execute(
                "INSERT INTO workout_exercises (workout_id, exercise_id, position) VALUES (?, ?, ?)",
                listOf(workoutId, ref.exerciseId, ref.position),
            )
        }
    }

    override fun updateWorkoutSchedule(workoutId: String, days: Set<DayOfWeek>) = transaction {
        sql.execute("DELETE FROM workout_schedule WHERE workout_id = ?", listOf(workoutId))
        days.sortedBy(DayOfWeek::getValue).forEach { day ->
            sql.execute(
                "INSERT INTO workout_schedule (workout_id, weekday) VALUES (?, ?)",
                listOf(workoutId, day.value),
            )
        }
    }

    override fun insertSession(session: WorkoutSession) = transaction {
        sql.execute(
            "INSERT INTO workout_sessions (id, workout_id, workout_name, local_date, started_at, completed_at, status, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(session.id, session.workoutId, session.workoutName, session.localDate, session.startedAt, session.completedAt, session.status.name, session.note),
        )
        session.exercises.forEach { entry -> insertSessionExercise(session.id, entry) }
    }

    override fun getSession(id: String): WorkoutSession? = sql.query(
        "SELECT * FROM workout_sessions WHERE id = ?",
        listOf(id),
    ).firstOrNull()?.let { row ->
        row.toWorkoutSession(sql.query("SELECT * FROM session_exercises WHERE session_id = ? ORDER BY position", listOf(id)))
    }

    override fun updateSession(session: WorkoutSession) = transaction {
        sql.execute(
            "UPDATE workout_sessions SET workout_name = ?, local_date = ?, started_at = ?, completed_at = ?, status = ?, note = ? WHERE id = ?",
            listOf(session.workoutName, session.localDate, session.startedAt, session.completedAt, session.status.name, session.note, session.id),
        )
        session.exercises.forEach(::updateSessionExercise)
    }

    override fun listSessions(): List<WorkoutSession> = sql.query(
        "SELECT * FROM workout_sessions ORDER BY local_date DESC, started_at DESC",
    ).map { row ->
        val id = row.string("id")
        row.toWorkoutSession(sql.query("SELECT * FROM session_exercises WHERE session_id = ? ORDER BY position", listOf(id)))
    }

    private fun insertSessionExercise(sessionId: String, entry: SessionExercise) {
        sql.execute(
            "INSERT INTO session_exercises (id, session_id, position, planned_exercise_id, planned_exercise_name, planned_set_count, planned_rep_count, planned_weight_lb, performed_exercise_id, performed_exercise_name, performed_set_count, performed_rep_count, performed_weight_lb, performed_increase_lb, performed_decrease_lb, outcome, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                entry.id,
                sessionId,
                entry.position,
                entry.plannedExerciseId,
                entry.plannedExerciseName,
                entry.plannedSetCount,
                entry.plannedRepCount,
                entry.plannedWeightLb,
                entry.performedExerciseId,
                entry.performedExerciseName,
                entry.performedSetCount,
                entry.performedRepCount,
                entry.performedWeightLb,
                entry.performedIncreaseLb,
                entry.performedDecreaseLb,
                entry.outcome?.name,
                entry.note,
            ),
        )
    }

    private fun updateSessionExercise(entry: SessionExercise) {
        sql.execute(
            "UPDATE session_exercises SET position = ?, planned_exercise_id = ?, planned_exercise_name = ?, planned_set_count = ?, planned_rep_count = ?, planned_weight_lb = ?, performed_exercise_id = ?, performed_exercise_name = ?, performed_set_count = ?, performed_rep_count = ?, performed_weight_lb = ?, performed_increase_lb = ?, performed_decrease_lb = ?, outcome = ?, note = ? WHERE id = ?",
            listOf(
                entry.position,
                entry.plannedExerciseId,
                entry.plannedExerciseName,
                entry.plannedSetCount,
                entry.plannedRepCount,
                entry.plannedWeightLb,
                entry.performedExerciseId,
                entry.performedExerciseName,
                entry.performedSetCount,
                entry.performedRepCount,
                entry.performedWeightLb,
                entry.performedIncreaseLb,
                entry.performedDecreaseLb,
                entry.outcome?.name,
                entry.note,
                entry.id,
            ),
        )
    }

    private fun Map<String, Any?>.toExercise() = Exercise(
        id = string("id"),
        name = string("name"),
        setCount = int("set_count"),
        repCount = int("rep_count"),
        currentWeightLb = double("current_weight_lb"),
        increaseLb = double("increase_lb"),
        decreaseLb = double("decrease_lb"),
        archived = int("archived") != 0,
    )

    private fun Map<String, Any?>.toWorkout(): Workout {
        val id = string("id")
        return Workout(
            id = id,
            name = string("name"),
            archived = int("archived") != 0,
            exercises = sql.query("SELECT exercise_id, position FROM workout_exercises WHERE workout_id = ? ORDER BY position", listOf(id))
                .map { row -> WorkoutExerciseRef(row.string("exercise_id"), row.int("position")) },
            scheduledDays = sql.query("SELECT weekday FROM workout_schedule WHERE workout_id = ?", listOf(id))
                .map { row -> DayOfWeek.of(row.int("weekday")) }
                .toSet(),
        )
    }

    private fun Map<String, Any?>.toWorkoutSession(exerciseRows: List<Map<String, Any?>>) = WorkoutSession(
        id = string("id"),
        workoutId = string("workout_id"),
        workoutName = string("workout_name"),
        localDate = string("local_date"),
        startedAt = string("started_at"),
        completedAt = stringOrNull("completed_at"),
        status = SessionStatus.valueOf(string("status")),
        note = string("note"),
        exercises = exerciseRows.map { it.toSessionExercise(string("id")) },
    )

    private fun Map<String, Any?>.toSessionExercise(sessionId: String) = SessionExercise(
        id = string("id"),
        position = int("position"),
        plannedExerciseId = string("planned_exercise_id"),
        plannedExerciseName = string("planned_exercise_name"),
        plannedSetCount = int("planned_set_count"),
        plannedRepCount = int("planned_rep_count"),
        plannedWeightLb = double("planned_weight_lb"),
        performedExerciseId = string("performed_exercise_id"),
        performedExerciseName = string("performed_exercise_name"),
        performedSetCount = int("performed_set_count"),
        performedRepCount = int("performed_rep_count"),
        performedWeightLb = double("performed_weight_lb"),
        performedIncreaseLb = double("performed_increase_lb"),
        performedDecreaseLb = double("performed_decrease_lb"),
        outcome = stringOrNull("outcome")?.let(ExerciseOutcome::valueOf),
        note = string("note"),
    )

    private fun Map<String, Any?>.string(name: String): String = this[name]?.toString()
        ?: error("Missing non-null column: $name")

    private fun Map<String, Any?>.stringOrNull(name: String): String? = this[name]?.toString()

    private fun Map<String, Any?>.int(name: String): Int = when (val value = this[name]) {
        is Number -> value.toInt()
        else -> value?.toString()?.toIntOrNull() ?: error("Invalid integer column: $name")
    }

    private fun Map<String, Any?>.double(name: String): Double = when (val value = this[name]) {
        is Number -> value.toDouble()
        else -> value?.toString()?.toDoubleOrNull() ?: error("Invalid numeric column: $name")
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0
}
