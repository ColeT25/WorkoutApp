package com.workoutapp.agent

import com.workoutapp.core.WorkoutApplication
import com.workoutapp.core.WorkoutException
import com.workoutapp.storage.SqlWorkoutRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path

fun main(args: Array<String>) {
    val databasePath = args.valueAfter("--db")?.let(Path::of) ?: Path.of("workout-agent.db")
    val jsonl = args.contains("--jsonl")
    if (!jsonl) {
        println("Usage: workout-agent --db <path> --jsonl")
        return
    }

    val json = Json { prettyPrint = false }
    JdbcSqlExecutor(databasePath).use { executor ->
        val processor = JsonlCommandProcessor(WorkoutApplication(SqlWorkoutRepository(executor)))
        generateSequence(::readLine).forEach { line ->
            if (line.isBlank()) return@forEach
            val response = try {
                processor.execute(json.parseToJsonElement(line).jsonObject)
            } catch (error: WorkoutException) {
                buildJsonObject {
                    put("ok", false)
                    put("code", error.code)
                    put("error", error.message)
                }
            } catch (error: Throwable) {
                buildJsonObject {
                    put("ok", false)
                    put("code", "agent_error")
                    put("error", error.message ?: error::class.simpleName.orEmpty())
                }
            }
            println(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), response))
        }
    }
}

private fun Array<String>.valueAfter(flag: String): String? = indexOf(flag).takeIf { it >= 0 && it + 1 < size }?.let { this[it + 1] }
