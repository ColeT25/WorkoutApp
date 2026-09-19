package com.workoutapp.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.workoutapp.storage.DatabaseSchema
import com.workoutapp.storage.SqlExecutor

class AndroidSqlExecutor(
    context: Context,
) : SqlExecutor {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "workoutapp.db", null, DatabaseSchema.CURRENT_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            DatabaseSchema.statements.forEach(db::execSQL)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            DatabaseSchema.migrationStatements(oldVersion, newVersion).forEach(db::execSQL)
        }
    }

    private val database: SQLiteDatabase = helper.writableDatabase.apply {
        execSQL("PRAGMA foreign_keys = ON")
    }

    override fun execute(sql: String, args: List<Any?>) {
        database.execSQL(sql, args.toTypedArray())
    }

    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> {
        val stringArgs = args.map { it?.toString() }.toTypedArray()
        database.rawQuery(sql, stringArgs).use { cursor ->
            val rows = mutableListOf<Map<String, Any?>>()
            while (cursor.moveToNext()) {
                rows += buildMap {
                    cursor.columnNames.forEachIndexed { index, name ->
                        put(name, cursor.value(index))
                    }
                }
            }
            return rows
        }
    }

    override fun <T> transaction(block: () -> T): T {
        database.beginTransaction()
        return try {
            block().also { database.setTransactionSuccessful() }
        } finally {
            database.endTransaction()
        }
    }

    override fun close() {
        helper.close()
    }
}

private fun android.database.Cursor.value(index: Int): Any? = when (getType(index)) {
    android.database.Cursor.FIELD_TYPE_NULL -> null
    android.database.Cursor.FIELD_TYPE_INTEGER -> getLong(index)
    android.database.Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
    android.database.Cursor.FIELD_TYPE_BLOB -> getBlob(index)
    else -> getString(index)
}
