package com.workoutapp.agent

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsonlCommandProcessorTest {
    private val json = Json

    @Test
    fun `commands survive agent restart and finishing is idempotent`() {
        val directory = Files.createTempDirectory("workoutapp-agent-test")
        val database = directory.resolve("workout.db")
        var exerciseId: String
        var workoutId: String
        var sessionId: String

        JdbcSqlExecutor(database).use { executor ->
            val processor = JsonlCommandProcessor(com.workoutapp.core.WorkoutApplication(com.workoutapp.storage.SqlWorkoutRepository(executor)))
            exerciseId = objectResult(processor, """
                {"op":"create_exercise","name":"Bench Press","sets":3,"reps":5,"weightLb":100,"increaseLb":5,"decreaseLb":10}
            """).string("id")
            workoutId = objectResult(processor, """
                {"op":"create_workout","name":"Push Day"}
            """).string("id")
            objectResult(processor, """
                {"op":"add_exercise","workoutId":"$workoutId","exerciseId":"$exerciseId"}
            """)
            objectResult(processor, """
                {"op":"schedule_workout","workoutId":"$workoutId","days":["MONDAY","FRIDAY"]}
            """)
            val started = objectResult(processor, """
                {"op":"start_session","workoutId":"$workoutId","date":"2026-09-17"}
            """)
            sessionId = started.string("id")
            assertEquals("ACTIVE", started.string("status"))
            objectResult(processor, """
                {"op":"mark_exercise","sessionId":"$sessionId","plannedExerciseId":"$exerciseId","outcome":"completed","note":"Felt strong"}
            """)
            objectResult(processor, """
                {"op":"set_session_note","sessionId":"$sessionId","note":"Good session"}
            """)
        }

        JdbcSqlExecutor(database).use { executor ->
            val processor = JsonlCommandProcessor(com.workoutapp.core.WorkoutApplication(com.workoutapp.storage.SqlWorkoutRepository(executor)))
            val resumed = objectResult(processor, """{"op":"get_session","sessionId":"$sessionId"}""")
            assertEquals("ACTIVE", resumed.string("status"))
            assertEquals("Good session", resumed.string("note"))
            assertEquals("Felt strong", resumed.elements("exercises")[0].jsonObject.string("note"))

            val completed = objectResult(processor, """{"op":"finish_session","sessionId":"$sessionId"}""")
            assertEquals("COMPLETED", completed.string("status"))
            val secondFinish = objectResult(processor, """{"op":"finish_session","sessionId":"$sessionId"}""")
            assertEquals(completed.string("completedAt"), secondFinish.string("completedAt"))

            val exercise = arrayResult(processor, """{"op":"list_exercises"}""")[0].jsonObject
            assertEquals(105.0, exercise.double("weightLb"))
        }

        directory.toFile().deleteRecursively()
    }

    @Test
    fun `substitution uses substitute settings and preserves planned history`() {
        val directory = Files.createTempDirectory("workoutapp-substitution-test")
        val database = directory.resolve("workout.db")

        JdbcSqlExecutor(database).use { executor ->
            val processor = JsonlCommandProcessor(com.workoutapp.core.WorkoutApplication(com.workoutapp.storage.SqlWorkoutRepository(executor)))
            val plannedId = objectResult(processor, """
                {"op":"create_exercise","name":"Barbell Row","sets":4,"reps":8,"weightLb":80,"increaseLb":5,"decreaseLb":10}
            """).string("id")
            val substituteId = objectResult(processor, """
                {"op":"create_exercise","name":"Cable Row","sets":3,"reps":12,"weightLb":50,"increaseLb":2.5,"decreaseLb":5}
            """).string("id")
            val workoutId = objectResult(processor, """{"op":"create_workout","name":"Pull Day"}""").string("id")
            objectResult(processor, """{"op":"add_exercise","workoutId":"$workoutId","exerciseId":"$plannedId"}""")
            val sessionId = objectResult(processor, """
                {"op":"start_session","workoutId":"$workoutId","date":"2026-09-17","substitutions":{"$plannedId":"$substituteId"}}
            """).string("id")

            val started = objectResult(processor, """{"op":"get_session","sessionId":"$sessionId"}""")
            val entry = started.elements("exercises")[0].jsonObject
            assertEquals("Barbell Row", entry.string("plannedExerciseName"))
            assertEquals("Cable Row", entry.string("performedExerciseName"))
            assertEquals(50.0, entry.double("performedWeightLb"))

            objectResult(processor, """{"op":"mark_exercise","sessionId":"$sessionId","plannedExerciseId":"$plannedId","outcome":"completed"}""")
            objectResult(processor, """{"op":"finish_session","sessionId":"$sessionId"}""")
            val exercises = arrayResult(processor, """{"op":"list_exercises"}""")
            val substitute = exercises.first { it.jsonObject.string("id") == substituteId }.jsonObject
            assertEquals(52.5, substitute.double("weightLb"))
            val planned = exercises.first { it.jsonObject.string("id") == plannedId }.jsonObject
            assertEquals(80.0, planned.double("weightLb"))
        }

        directory.toFile().deleteRecursively()
    }

    private fun resultElement(processor: JsonlCommandProcessor, line: String) =
        assertNotNull(processor.execute(json.parseToJsonElement(line.trim()).jsonObject)["result"])

    private fun objectResult(processor: JsonlCommandProcessor, line: String): JsonObject = resultElement(processor, line).jsonObject

    private fun arrayResult(processor: JsonlCommandProcessor, line: String): JsonArray = resultElement(processor, line).jsonArray

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.double(name: String): Double = getValue(name).jsonPrimitive.double

    private fun JsonObject.elements(name: String) = getValue(name).jsonArray
}
