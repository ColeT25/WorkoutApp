package com.workoutapp.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.workoutapp.core.DEFAULT_DURATION_SECONDS
import com.workoutapp.core.Exercise
import com.workoutapp.core.ExerciseOutcome
import com.workoutapp.core.ExerciseTargetType
import com.workoutapp.core.WorkoutApplication
import com.workoutapp.core.WorkoutException
import com.workoutapp.core.WorkoutSession
import com.workoutapp.storage.SqlWorkoutRepository
import java.time.DayOfWeek
import java.time.LocalDate

enum class AppScreen {
    TODAY,
    PLANS,
    HISTORY,
    EXERCISE_FORM,
    WORKOUT_FORM,
    ACTIVE_SESSION,
}

data class WorkoutUiState(
    val screen: AppScreen = AppScreen.TODAY,
    val exercises: List<Exercise> = emptyList(),
    val workouts: List<com.workoutapp.core.Workout> = emptyList(),
    val todayWorkouts: List<com.workoutapp.core.Workout> = emptyList(),
    val history: List<WorkoutSession> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val editingExerciseId: String? = null,
    val editingWorkoutId: String? = null,
    val error: String? = null,
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = AndroidSqlExecutor(application)
    private val applicationCore = WorkoutApplication(SqlWorkoutRepository(executor))

    var state by mutableStateOf(WorkoutUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        val active = applicationCore.activeSession()
        state = state.copy(
            exercises = applicationCore.listExercises(),
            workouts = applicationCore.listWorkouts(),
            todayWorkouts = applicationCore.getTodayWorkouts(DayOfWeek.valueOf(LocalDate.now().dayOfWeek.name)),
            history = applicationCore.history(),
            activeSession = active,
        )
    }

    fun navigate(screen: AppScreen, exerciseId: String? = null, workoutId: String? = null) {
        state = state.copy(screen = screen, editingExerciseId = exerciseId, editingWorkoutId = workoutId, error = null)
    }

    fun createExercise(name: String, sets: Int, reps: Int, weightLb: Double, increaseLb: Double, decreaseLb: Double, targetType: ExerciseTargetType = ExerciseTargetType.REPS, durationSeconds: Int = DEFAULT_DURATION_SECONDS) = runAction {
        applicationCore.createExercise(name, sets, reps, weightLb, increaseLb, decreaseLb, targetType, durationSeconds)
        navigate(if (state.activeSession != null) AppScreen.ACTIVE_SESSION else AppScreen.PLANS)
    }

    fun updateExercise(id: String, name: String, sets: Int, reps: Int, weightLb: Double, increaseLb: Double, decreaseLb: Double, targetType: ExerciseTargetType = ExerciseTargetType.REPS, durationSeconds: Int = DEFAULT_DURATION_SECONDS) = runAction {
        applicationCore.updateExercise(id, name, sets, reps, weightLb, increaseLb, decreaseLb, targetType, durationSeconds)
        navigate(if (state.activeSession != null) AppScreen.ACTIVE_SESSION else AppScreen.PLANS)
    }

    fun createWorkout(name: String): String? {
        var id: String? = null
        runAction { id = applicationCore.createWorkout(name).id }
        return id
    }

    fun updateWorkout(id: String, name: String): Boolean = runAction {
        applicationCore.updateWorkout(id, name)
    }

    fun archiveExercise(id: String): Boolean = runAction { applicationCore.archiveExercise(id) }

    fun archiveWorkout(id: String): Boolean = runAction { applicationCore.archiveWorkout(id) }

    fun addExercise(workoutId: String, exerciseId: String) = runAction { applicationCore.addExerciseToWorkout(workoutId, exerciseId) }

    fun removeExercise(workoutId: String, exerciseId: String) = runAction { applicationCore.removeExerciseFromWorkout(workoutId, exerciseId) }

    fun reorderExercises(workoutId: String, exerciseIds: List<String>): Boolean = runAction {
        applicationCore.reorderWorkoutExercises(workoutId, exerciseIds)
    }

    fun assignDays(workoutId: String, days: Set<DayOfWeek>) = runAction { applicationCore.assignWorkoutToDays(workoutId, days) }

    fun startSession(workoutId: String, substitutions: Map<String, String> = emptyMap(), additionalExerciseIds: List<String> = emptyList()): Boolean = runAction {
        applicationCore.startSession(workoutId, substitutions = substitutions, additionalExerciseIds = additionalExerciseIds)
        navigate(AppScreen.ACTIVE_SESSION)
    }

    fun markExercise(sessionId: String, plannedExerciseId: String, outcome: ExerciseOutcome, note: String) = runAction {
        applicationCore.recordExerciseOutcome(sessionId, plannedExerciseId, outcome, note)
    }

    fun addSessionExercise(sessionId: String, exerciseId: String): Boolean = runAction {
        applicationCore.addExerciseToSession(sessionId, exerciseId)
    }

    fun removeSessionExercise(sessionId: String, sessionExerciseId: String): Boolean = runAction {
        applicationCore.removeExerciseFromSession(sessionId, sessionExerciseId)
    }

    fun setSessionNote(sessionId: String, note: String) = runAction { applicationCore.setSessionNote(sessionId, note) }

    fun finishSession(sessionId: String, note: String) = runAction {
        applicationCore.finishSession(sessionId, note)
        navigate(AppScreen.TODAY)
    }

    fun cancelSession(sessionId: String) = runAction {
        applicationCore.cancelSession(sessionId)
        navigate(AppScreen.TODAY)
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    override fun onCleared() {
        executor.close()
        super.onCleared()
    }

    private fun runAction(action: () -> Unit): Boolean {
        try {
            action()
            refresh()
            return true
        } catch (error: WorkoutException) {
            state = state.copy(error = error.message)
        } catch (error: IllegalArgumentException) {
            state = state.copy(error = error.message ?: "Invalid input")
        }
        return false
    }
}
