package com.workoutapp.storage

interface SqlExecutor : AutoCloseable {
    fun execute(sql: String, args: List<Any?> = emptyList())

    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, Any?>>

    fun <T> transaction(block: () -> T): T

    override fun close() = Unit
}
