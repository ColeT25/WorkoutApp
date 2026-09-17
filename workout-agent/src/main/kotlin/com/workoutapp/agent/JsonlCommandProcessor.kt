package com.workoutapp.agent

import com.workoutapp.core.ExerciseOutcome
import com.workoutapp.core.WorkoutApplication
import java.time.DayOfWeek
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class JsonlCommandProcessor(
    private val application: WorkoutApplication,
) {
    fun execute(command: JsonObject): JsonObject {
        val operation = command.requiredString("op")
        val result = when (operation) {
            "create_exercise" -> application.createExercise(
                name = command.requiredString("name"),
                setCount = command.requiredInt("sets"),
                repCount = command.requiredInt("reps"),
                currentWeightLb = command.requiredDouble("weightLb"),
                increaseLb = command.requiredDouble("increaseLb"),
                decreaseLb = command.requiredDouble("decreaseLb"),
            ).toJson()
            "update_exercise" -> application.updateExercise(
                id = command.requiredString("id"),
                name = command.requiredString("name"),
                setCount = command.requiredInt("sets"),
                repCount = command.requiredInt("reps"),
                currentWeightLb = command.requiredDouble("weightLb"),
                increaseLb = command.requiredDouble("increaseLb"),
                decreaseLb = command.requiredDouble("decreaseLb"),
            ).toJson()
            "archive_exercise" -> application.archiveExercise(command.requiredString("id")).toJson()
            "list_exercises" -> buildJsonArray { application.listExercises(command.boolean("includeArchived")).forEach { add(it.toJson()) } }
            "create_workout" -> application.createWorkout(command.requiredString("name")).toJson()
            "update_workout" -> application.updateWorkout(command.requiredString("id"), command.requiredString("name")).toJson()
            "archive_workout" -> application.archiveWorkout(command.requiredString("id")).toJson()
            "list_workouts" -> buildJsonArray { application.listWorkouts(command.boolean("includeArchived")).forEach { add(it.toJson()) } }
            "add_exercise" -> application.addExerciseToWorkout(command.requiredString("workoutId"), command.requiredString("exerciseId")).toJson()
            "remove_exercise" -> application.removeExerciseFromWorkout(command.requiredString("workoutId"), command.requiredString("exerciseId")).toJson()
            "reorder_exercises" -> application.reorderWorkoutExercises(
                command.requiredString("workoutId"),
                command.requiredArray("exerciseIds").map { it.jsonPrimitive.content },
            ).toJson()
            "schedule_workout" -> application.assignWorkoutToDays(
                command.requiredString("workoutId"),
                command.requiredArray("days").map { day -> day.jsonPrimitive.content.toDayOfWeek() }.toSet(),
            ).toJson()
            "today" -> buildJsonArray { application.getTodayWorkouts(command.requiredString("day").toDayOfWeek()).forEach { add(it.toJson()) } }
            "start_session" -> application.startSession(
                workoutId = command.requiredString("workoutId"),
                localDate = command.requiredString("date"),
                substitutions = command.substitutions(),
            ).toJson()
            "get_session" -> application.getSession(command.requiredString("sessionId"))?.toJson() ?: error("Session not found")
            "mark_exercise" -> application.recordExerciseOutcome(
                sessionId = command.requiredString("sessionId"),
                plannedExerciseId = command.requiredString("plannedExerciseId"),
                outcome = ExerciseOutcome.valueOf(command.requiredString("outcome").uppercase()),
                note = command.optionalString("note") ?: "",
            ).toJson()
            "set_session_note" -> application.setSessionNote(command.requiredString("sessionId"), command.optionalString("note") ?: "").toJson()
            "finish_session" -> application.finishSession(command.requiredString("sessionId")).toJson()
            "history" -> buildJsonArray { application.history().forEach { add(it.toJson()) } }
            "state" -> buildJsonObject {
                putJsonArray("exercises") { application.listExercises(includeArchived = true).forEach { add(it.toJson()) } }
                putJsonArray("workouts") { application.listWorkouts(includeArchived = true).forEach { add(it.toJson()) } }
                putJsonArray("sessions") { application.history().forEach { add(it.toJson()) } }
                application.activeSession()?.let { put("activeSession", it.toJson()) }
            }
            else -> error("Unknown operation: $operation")
        }
        return buildJsonObject {
            put("ok", true)
            put("op", operation)
            put("result", result)
        }
    }

    private fun ExerciseOutcome.toJson(): JsonPrimitive = JsonPrimitive(name)

    private fun com.workoutapp.core.Exercise.toJson() = buildJsonObject {
        put("id", id)
        put("name", name)
        put("sets", setCount)
        put("reps", repCount)
        put("weightLb", currentWeightLb)
        put("increaseLb", increaseLb)
        put("decreaseLb", decreaseLb)
        put("archived", archived)
    }

    private fun com.workoutapp.core.Workout.toJson() = buildJsonObject {
        put("id", id)
        put("name", name)
        put("archived", archived)
        putJsonArray("exerciseIds") { exercises.sortedBy { it.position }.forEach { add(JsonPrimitive(it.exerciseId)) } }
        putJsonArray("days") { scheduledDays.sortedBy(DayOfWeek::getValue).forEach { add(JsonPrimitive(it.name)) } }
    }

    private fun com.workoutapp.core.WorkoutSession.toJson() = buildJsonObject {
        put("id", id)
        put("workoutId", workoutId)
        put("workoutName", workoutName)
        put("date", localDate)
        put("startedAt", startedAt)
        put("completedAt", completedAt)
        put("status", status.name)
        put("note", note)
        putJsonArray("exercises") { exercises.sortedBy { it.position }.forEach { entry ->
            add(buildJsonObject {
                put("id", entry.id)
                put("plannedExerciseId", entry.plannedExerciseId)
                put("plannedExerciseName", entry.plannedExerciseName)
                put("plannedSets", entry.plannedSetCount)
                put("plannedReps", entry.plannedRepCount)
                put("plannedWeightLb", entry.plannedWeightLb)
                put("performedExerciseId", entry.performedExerciseId)
                put("performedExerciseName", entry.performedExerciseName)
                put("performedSets", entry.performedSetCount)
                put("performedReps", entry.performedRepCount)
                put("performedWeightLb", entry.performedWeightLb)
                put("performedIncreaseLb", entry.performedIncreaseLb)
                put("performedDecreaseLb", entry.performedDecreaseLb)
                put("outcome", entry.outcome?.name)
                put("note", entry.note)
            })
        } }
    }

    private fun JsonObject.requiredString(name: String): String = optionalString(name)
        ?: error("$name is required")

    private fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredInt(name: String): Int = get(name)?.jsonPrimitive?.int
        ?: error("$name is required")

    private fun JsonObject.requiredDouble(name: String): Double = get(name)?.jsonPrimitive?.double
        ?: error("$name is required")

    private fun JsonObject.boolean(name: String): Boolean = get(name)?.jsonPrimitive?.booleanOrNull ?: false

    private fun JsonObject.requiredArray(name: String): JsonArray = get(name)?.jsonArray
        ?: error("$name is required")

    private fun JsonObject.substitutions(): Map<String, String> = get("substitutions")?.jsonObject
        ?.mapValues { (_, value) -> value.jsonPrimitive.content }
        ?: emptyMap()

    private fun String.toDayOfWeek(): DayOfWeek = toIntOrNull()?.let(DayOfWeek::of)
        ?: DayOfWeek.valueOf(uppercase())
}
