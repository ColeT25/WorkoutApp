package com.workoutapp.storage

object DatabaseSchema {
    const val CURRENT_VERSION = 2

    val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS exercises (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            set_count INTEGER NOT NULL,
            rep_count INTEGER NOT NULL,
            current_weight_lb REAL NOT NULL,
            increase_lb REAL NOT NULL,
            decrease_lb REAL NOT NULL,
            target_type TEXT NOT NULL DEFAULT 'REPS',
            duration_seconds INTEGER NOT NULL DEFAULT 60,
            archived INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS workouts (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            archived INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS workout_exercises (
            workout_id TEXT NOT NULL,
            exercise_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            PRIMARY KEY (workout_id, exercise_id),
            FOREIGN KEY (workout_id) REFERENCES workouts(id),
            FOREIGN KEY (exercise_id) REFERENCES exercises(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS workout_schedule (
            workout_id TEXT NOT NULL,
            weekday INTEGER NOT NULL,
            PRIMARY KEY (workout_id, weekday),
            FOREIGN KEY (workout_id) REFERENCES workouts(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS workout_sessions (
            id TEXT PRIMARY KEY NOT NULL,
            workout_id TEXT NOT NULL,
            workout_name TEXT NOT NULL,
            local_date TEXT NOT NULL,
            started_at TEXT NOT NULL,
            completed_at TEXT,
            status TEXT NOT NULL,
            note TEXT NOT NULL DEFAULT '',
            FOREIGN KEY (workout_id) REFERENCES workouts(id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS session_exercises (
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
            note TEXT NOT NULL DEFAULT '',
            planned_target_type TEXT NOT NULL DEFAULT 'REPS',
            planned_duration_seconds INTEGER NOT NULL DEFAULT 60,
            performed_target_type TEXT NOT NULL DEFAULT 'REPS',
            performed_duration_seconds INTEGER NOT NULL DEFAULT 60,
            is_ad_hoc INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (session_id) REFERENCES workout_sessions(id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_sessions_date ON workout_sessions(local_date, started_at)",
        "CREATE INDEX IF NOT EXISTS idx_session_exercises_session ON session_exercises(session_id, position)",
        "CREATE TABLE IF NOT EXISTS workoutapp_schema (version INTEGER NOT NULL)",
        "INSERT INTO workoutapp_schema (version) SELECT $CURRENT_VERSION WHERE NOT EXISTS (SELECT 1 FROM workoutapp_schema)",
    )

    fun migrationStatements(fromVersion: Int, toVersion: Int): List<String> = buildList {
        if (fromVersion < 2 && toVersion >= 2) {
            add("ALTER TABLE exercises ADD COLUMN target_type TEXT NOT NULL DEFAULT 'REPS'")
            add("ALTER TABLE exercises ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 60")
            add("ALTER TABLE session_exercises ADD COLUMN planned_target_type TEXT NOT NULL DEFAULT 'REPS'")
            add("ALTER TABLE session_exercises ADD COLUMN planned_duration_seconds INTEGER NOT NULL DEFAULT 60")
            add("ALTER TABLE session_exercises ADD COLUMN performed_target_type TEXT NOT NULL DEFAULT 'REPS'")
            add("ALTER TABLE session_exercises ADD COLUMN performed_duration_seconds INTEGER NOT NULL DEFAULT 60")
            add("ALTER TABLE session_exercises ADD COLUMN is_ad_hoc INTEGER NOT NULL DEFAULT 0")
            add("CREATE TABLE IF NOT EXISTS workoutapp_schema (version INTEGER NOT NULL)")
            add("INSERT INTO workoutapp_schema (version) SELECT $CURRENT_VERSION WHERE NOT EXISTS (SELECT 1 FROM workoutapp_schema)")
            add("UPDATE workoutapp_schema SET version = $CURRENT_VERSION")
        }
    }
}

object DatabaseSchemaManager {
    fun ensureLatest(sql: SqlExecutor) {
        val hasExercisesTable = sql.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'exercises'",
        ).isNotEmpty()
        if (!hasExercisesTable) {
            DatabaseSchema.statements.forEach(sql::execute)
            return
        }

        val hasSchemaTable = sql.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'workoutapp_schema'",
        ).isNotEmpty()
        val hasTargetColumn = sql.query("PRAGMA table_info(exercises)")
            .any { it["name"]?.toString() == "target_type" }
        val version = if (hasSchemaTable) {
            sql.query("SELECT version FROM workoutapp_schema LIMIT 1").firstOrNull()?.get("version")?.toString()?.toIntOrNull() ?: 1
        } else if (hasTargetColumn) {
            DatabaseSchema.CURRENT_VERSION
        } else {
            1
        }
        if (version < DatabaseSchema.CURRENT_VERSION) {
            DatabaseSchema.migrationStatements(version, DatabaseSchema.CURRENT_VERSION).forEach(sql::execute)
        }
        DatabaseSchema.statements.forEach(sql::execute)
    }
}
