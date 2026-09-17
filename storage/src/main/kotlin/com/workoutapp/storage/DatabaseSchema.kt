package com.workoutapp.storage

object DatabaseSchema {
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
            FOREIGN KEY (session_id) REFERENCES workout_sessions(id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_sessions_date ON workout_sessions(local_date, started_at)",
        "CREATE INDEX IF NOT EXISTS idx_session_exercises_session ON session_exercises(session_id, position)",
    )
}
