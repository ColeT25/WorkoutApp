package com.workoutapp.storage

import com.workoutapp.core.ExerciseTargetType
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DatabaseMigrationTest {
    @Test
    fun `legacy schema migrates without losing exercises or session history`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val sql = TestSqlExecutor(connection)
            sql.execute(
                """
                CREATE TABLE exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    set_count INTEGER NOT NULL,
                    rep_count INTEGER NOT NULL,
                    current_weight_lb REAL NOT NULL,
                    increase_lb REAL NOT NULL,
                    decrease_lb REAL NOT NULL,
                    archived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            sql.execute(
                """
                CREATE TABLE workout_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    workout_id TEXT NOT NULL,
                    workout_name TEXT NOT NULL,
                    local_date TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    completed_at TEXT,
                    status TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            sql.execute(
                """
                CREATE TABLE session_exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    session_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    planned_exercise_id TEXT NOT NULL,
                    planned_exercise_name TEXT NOT NULL,
                    planned_set_count INTEGER NOT NULL,
                    planned_rep_count INTEGER NOT NULL,
                    planned_weight_lb REAL NOT NULL,
                    performed_exercise_id TEXT NOT NULL,
                    performed_exercise_name TEXT NOT NULL,
                    performed_set_count INTEGER NOT NULL,
                    performed_rep_count INTEGER NOT NULL,
                    performed_weight_lb REAL NOT NULL,
                    performed_increase_lb REAL NOT NULL,
                    performed_decrease_lb REAL NOT NULL,
                    outcome TEXT,
                    note TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            sql.execute(
                "INSERT INTO workout_sessions (id, workout_id, workout_name, local_date, started_at, completed_at, status, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                listOf("session-1", "workout-1", "Push Day", "2026-09-17", "2026-09-17T12:00:00Z", "2026-09-17T13:00:00Z", "COMPLETED", "Finished"),
            )
            sql.execute(
                "INSERT INTO exercises (id, name, set_count, rep_count, current_weight_lb, increase_lb, decrease_lb, archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                listOf("exercise-1", "Bench Press", 3, 5, 100.0, 5.0, 2.5, 0),
            )
            sql.execute(
                "INSERT INTO session_exercises (id, session_id, position, planned_exercise_id, planned_exercise_name, planned_set_count, planned_rep_count, planned_weight_lb, performed_exercise_id, performed_exercise_name, performed_set_count, performed_rep_count, performed_weight_lb, performed_increase_lb, performed_decrease_lb, outcome, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                listOf("session-exercise-1", "session-1", 0, "exercise-1", "Bench Press", 3, 5, 100.0, "exercise-1", "Bench Press", 3, 5, 100.0, 5.0, 2.5, "COMPLETED", "Strong session"),
            )

            DatabaseSchemaManager.ensureLatest(sql)
            val repository = SqlWorkoutRepository(sql)
            val exercise = repository.getExercise("exercise-1")!!
            val sessionExercise = repository.querySessionExercise("session-exercise-1")

            assertEquals("Bench Press", exercise.name)
            assertEquals(100.0, exercise.currentWeightLb)
            assertEquals(ExerciseTargetType.REPS, exercise.targetType)
            assertEquals(60, exercise.durationSeconds)
            assertEquals("Strong session", sessionExercise.note)
            assertEquals(ExerciseTargetType.REPS, sessionExercise.performedTargetType)
            assertEquals(60, sessionExercise.performedDurationSeconds)
            assertFalse(sessionExercise.isAdHoc)
        }
    }

    private class TestSqlExecutor(private val connection: Connection) : SqlExecutor {
        override fun execute(sql: String, args: List<Any?>) {
            connection.prepareStatement(sql).use { statement ->
                statement.bind(args)
                statement.executeUpdate()
            }
        }

        override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> = connection.prepareStatement(sql).use { statement ->
            statement.bind(args)
            statement.executeQuery().use { resultSet ->
                val metadata = resultSet.metaData
                buildList {
                    while (resultSet.next()) {
                        add(buildMap {
                            for (index in 1..metadata.columnCount) {
                                put(metadata.getColumnLabel(index), resultSet.getObject(index))
                            }
                        })
                    }
                }
            }
        }

        override fun <T> transaction(block: () -> T): T = block()

        private fun PreparedStatement.bind(args: List<Any?>) {
            args.forEachIndexed { index, value -> setObject(index + 1, value) }
        }
    }
}

private fun SqlWorkoutRepository.querySessionExercise(id: String) = getSession("session-1")!!.exercises.single { it.id == id }
