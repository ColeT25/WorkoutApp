package com.workoutapp.agent

import com.workoutapp.storage.SqlExecutor
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

class JdbcSqlExecutor(
    databasePath: Path,
) : SqlExecutor {
    private val connection: Connection

    init {
        databasePath.toAbsolutePath().parent?.let(Files::createDirectories)
        connection = DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
        }
    }

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
            val rows = mutableListOf<Map<String, Any?>>()
            while (resultSet.next()) {
                rows += buildMap {
                    for (index in 1..metadata.columnCount) {
                        put(metadata.getColumnLabel(index), resultSet.getObject(index))
                    }
                }
            }
            rows
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override fun close() {
        connection.close()
    }

    private fun PreparedStatement.bind(args: List<Any?>) {
        args.forEachIndexed { index, value ->
            when (value) {
                null -> setObject(index + 1, null)
                is String -> setString(index + 1, value)
                is Int -> setInt(index + 1, value)
                is Long -> setLong(index + 1, value)
                is Double -> setDouble(index + 1, value)
                is Float -> setDouble(index + 1, value.toDouble())
                is Boolean -> setInt(index + 1, if (value) 1 else 0)
                else -> setObject(index + 1, value)
            }
        }
    }
}
