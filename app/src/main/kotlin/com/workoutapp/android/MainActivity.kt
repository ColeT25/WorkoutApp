package com.workoutapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.workoutapp.core.Exercise
import com.workoutapp.core.ExerciseOutcome
import com.workoutapp.core.SessionExercise
import com.workoutapp.core.Workout
import com.workoutapp.core.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<WorkoutViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutAppTheme {
                WorkoutApp(viewModel)
            }
        }
    }
}

@Composable
private fun WorkoutApp(viewModel: WorkoutViewModel) {
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    when (state.screen) {
        AppScreen.TODAY -> MainScaffold(viewModel, AppScreen.TODAY, snackbarHostState) { TodayScreen(state, viewModel) }
        AppScreen.PLANS -> MainScaffold(viewModel, AppScreen.PLANS, snackbarHostState) { PlansScreen(state, viewModel) }
        AppScreen.HISTORY -> MainScaffold(viewModel, AppScreen.HISTORY, snackbarHostState) { HistoryScreen(state) }
        AppScreen.EXERCISE_FORM -> FormScaffold(
            snackbarHostState,
            "Exercise",
            onBack = { viewModel.navigate(if (state.activeSession != null) AppScreen.ACTIVE_SESSION else AppScreen.PLANS) },
        ) {
            ExerciseFormScreen(state, viewModel)
        }
        AppScreen.WORKOUT_FORM -> FormScaffold(snackbarHostState, "Workout", onBack = { viewModel.navigate(AppScreen.PLANS) }) {
            WorkoutFormScreen(state, viewModel)
        }
        AppScreen.ACTIVE_SESSION -> FormScaffold(snackbarHostState, "Active workout", onBack = { viewModel.navigate(AppScreen.TODAY) }) {
            ActiveSessionScreen(state, viewModel)
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: WorkoutViewModel,
    selected: AppScreen,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == AppScreen.TODAY,
                    onClick = { viewModel.navigate(AppScreen.TODAY) },
                    icon = { Icon(Icons.Default.Today, contentDescription = "Today") },
                    label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = selected == AppScreen.PLANS,
                    onClick = { viewModel.navigate(AppScreen.PLANS) },
                    icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Plans") },
                    label = { Text("Plans") },
                )
                NavigationBarItem(
                    selected = selected == AppScreen.HISTORY,
                    onClick = { viewModel.navigate(AppScreen.HISTORY) },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormScaffold(
    snackbarHostState: SnackbarHostState,
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } },
    )
}

@Composable
private fun TodayScreen(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    val today = LocalDate.now()
    var workoutToStart by remember { mutableStateOf<Workout?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(today.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.activeSession?.let { session ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Workout in progress", fontWeight = FontWeight.Bold)
                        Text(session.workoutName)
                        Button(onClick = { viewModel.navigate(AppScreen.ACTIVE_SESSION) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Resume workout")
                        }
                    }
                }
            }
        }
        item { Text("Scheduled workouts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.todayWorkouts.isEmpty()) {
            item { EmptyState("No workouts scheduled for today.", "Create one from Plans.") }
        } else {
            items(state.todayWorkouts, key = { it.id }) { workout ->
                WorkoutCard(
                    workout = workout,
                    exerciseNames = state.exercises.associate { it.id to it.name },
                    enabled = state.activeSession == null,
                    onStart = { workoutToStart = workout },
                )
            }
        }
    }
    workoutToStart?.let { workout ->
        WorkoutStartDialog(
            workout = workout,
            exercises = state.exercises,
            onDismiss = { workoutToStart = null },
            onStart = { substitutions ->
                if (viewModel.startSession(workout.id, substitutions)) workoutToStart = null
            },
        )
    }
}

@Composable
private fun WorkoutStartDialog(
    workout: Workout,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onStart: (Map<String, String>) -> Unit,
) {
    val exerciseById = exercises.associateBy { it.id }
    var substitutions by remember(workout.id) { mutableStateOf(emptyMap<String, String>()) }
    var choosingFor by remember(workout.id) { mutableStateOf<String?>(null) }

    choosingFor?.let { plannedId ->
        val usedIds = workout.exercises.map { substitutions[it.exerciseId] ?: it.exerciseId }.toSet()
        val selectedId = substitutions[plannedId] ?: plannedId
        val options = exercises.filter { !it.archived && (it.id == selectedId || it.id !in usedIds) }
        AlertDialog(
            onDismissRequest = { choosingFor = null },
            title = { Text("Choose substitute") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This replacement applies to this workout only.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    options.forEach { exercise ->
                        TextButton(
                            onClick = {
                                substitutions = substitutions + (plannedId to exercise.id)
                                choosingFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text("${exercise.setCount} sets × ${exercise.repCount} reps  ·  ${exercise.currentWeightLb.prettyLb()} lb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingFor = null }) { Text("Back") } },
        )
    } ?: AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review ${workout.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optional substitutions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                workout.exercises.sortedBy { it.position }.forEach { ref ->
                    val planned = exerciseById[ref.exerciseId]
                    val performed = exerciseById[substitutions[ref.exerciseId] ?: ref.exerciseId]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(planned?.name ?: "Unavailable exercise", fontWeight = FontWeight.SemiBold)
                            if (performed != null && performed.id != planned?.id) {
                                Text("Will perform: ${performed.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = { choosingFor = ref.exerciseId }) { Text("Substitute") }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(onClick = { onStart(substitutions) }) { Text("Start workout") } },
    )
}

@Composable
private fun PlansScreen(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    var workoutToStart by remember { mutableStateOf<Workout?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Plans", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Build reusable workouts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { viewModel.navigate(AppScreen.EXERCISE_FORM) }) { Icon(Icons.Default.Add, "New exercise") }
                    IconButton(onClick = { viewModel.navigate(AppScreen.WORKOUT_FORM) }) { Icon(Icons.Default.CalendarMonth, "New workout") }
                }
            }
        }
        item { Text("Workouts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.workouts.isEmpty()) {
            item { EmptyState("No workouts yet.", "Use the calendar button to create one.") }
        } else {
            items(state.workouts, key = { it.id }) { workout ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(workout.name, fontWeight = FontWeight.Bold)
                                Text("${workout.exercises.size} exercises  ·  ${workout.scheduledDays.displayDays()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.navigate(AppScreen.WORKOUT_FORM, workoutId = workout.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit workout", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Button(
                            onClick = { workoutToStart = workout },
                            enabled = state.activeSession == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Start once")
                        }
                    }
                }
            }
        }
        item { Text("Exercises", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.exercises.isEmpty()) {
            item { EmptyState("No exercises yet.", "Use the plus button to create one.") }
        } else {
            items(state.exercises, key = { it.id }) { exercise ->
                Card(Modifier.fillMaxWidth().clickable { viewModel.navigate(AppScreen.EXERCISE_FORM, exerciseId = exercise.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, fontWeight = FontWeight.Bold)
                            Text("${exercise.setCount} sets × ${exercise.repCount} reps  ·  ${exercise.currentWeightLb.prettyLb()} lb", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Edit exercise", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    workoutToStart?.let { workout ->
        WorkoutStartDialog(
            workout = workout,
            exercises = state.exercises,
            onDismiss = { workoutToStart = null },
            onStart = { substitutions ->
                if (viewModel.startSession(workout.id, substitutions)) workoutToStart = null
            },
        )
    }
}

@Composable
private fun HistoryScreen(state: WorkoutUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("History", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Completed sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.history.isEmpty()) {
            item { EmptyState("No completed workouts yet.", "Your finished sessions will appear here.") }
        } else {
            items(state.history, key = { it.id }) { session -> HistoryCard(session) }
        }
    }
}

@Composable
private fun WorkoutCard(workout: Workout, exerciseNames: Map<String, String>, enabled: Boolean, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${workout.exercises.size} exercises  ·  ${workout.scheduledDays.displayDays()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                workout.exercises.sortedBy { it.position }.joinToString("  •  ") { exerciseNames[it.exerciseId] ?: "Archived exercise" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start workout")
            }
        }
    }
}

@Composable
private fun HistoryCard(session: WorkoutSession) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.workoutName, fontWeight = FontWeight.Bold)
                Text(session.localDate, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${session.exercises.size} exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
            session.exercises.forEach { entry ->
                val status = entry.outcome?.name?.lowercase()?.replace('_', ' ') ?: "unknown"
                Text("${entry.performedExerciseName}  ·  ${entry.performedWeightLb.prettyLb()} lb  ·  $status", style = MaterialTheme.typography.bodySmall)
            }
            if (session.note.isNotBlank()) Text(session.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExerciseFormScreen(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    val existing = state.exercises.firstOrNull { it.id == state.editingExerciseId }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var sets by remember(existing?.id) { mutableStateOf(existing?.setCount?.toString() ?: "3") }
    var reps by remember(existing?.id) { mutableStateOf(existing?.repCount?.toString() ?: "8") }
    var weight by remember(existing?.id) { mutableStateOf(existing?.currentWeightLb?.prettyLb() ?: "0") }
    var increase by remember(existing?.id) { mutableStateOf(existing?.increaseLb?.prettyLb() ?: "0") }
    var decrease by remember(existing?.id) { mutableStateOf(existing?.decreaseLb?.prettyLb() ?: "0") }

    FormColumn {
        Text(if (existing == null) "New exercise" else "Edit exercise", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Settings are shared everywhere this exercise is used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
            NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
        }
        NumberField("Current weight (lb)", weight, { weight = it }, Modifier.fillMaxWidth())
        Text("Progressive overload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Increase by (lb)", increase, { increase = it }, Modifier.weight(1f))
            NumberField("Decrease by (lb)", decrease, { decrease = it }, Modifier.weight(1f))
        }
        Button(
            onClick = {
                val parsedSets = sets.toIntOrNull() ?: 0
                val parsedReps = reps.toIntOrNull() ?: 0
                val parsedWeight = weight.toDoubleOrNull() ?: -1.0
                val parsedIncrease = increase.toDoubleOrNull() ?: -1.0
                val parsedDecrease = decrease.toDoubleOrNull() ?: -1.0
                if (existing == null) viewModel.createExercise(name, parsedSets, parsedReps, parsedWeight, parsedIncrease, parsedDecrease)
                else viewModel.updateExercise(existing.id, name, parsedSets, parsedReps, parsedWeight, parsedIncrease, parsedDecrease)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Save exercise")
        }
        existing?.let {
            OutlinedButton(onClick = { if (viewModel.archiveExercise(it.id)) viewModel.navigate(AppScreen.PLANS) }, modifier = Modifier.fillMaxWidth()) {
                Text("Archive exercise")
            }
        }
    }
}

@Composable
private fun WorkoutFormScreen(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    val existing = state.workouts.firstOrNull { it.id == state.editingWorkoutId }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var selectedIds by remember(existing?.id) {
        mutableStateOf<List<String>>(existing?.exercises?.sortedBy { it.position }?.map { it.exerciseId } ?: emptyList())
    }
    var selectedDays by remember(existing?.id) { mutableStateOf(existing?.scheduledDays ?: emptySet()) }

    FormColumn {
        Text(if (existing == null) "New workout" else "Edit workout", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Workout name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Repeat on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DayOfWeek.values().forEach { day ->
                FilterChip(selected = day in selectedDays, onClick = { selectedDays = selectedDays.toggle(day) }, label = { Text(day.shortLabel()) })
            }
        }
        Text("Exercise order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (selectedIds.isEmpty()) {
            Text("No exercises selected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            selectedIds.forEachIndexed { index, id ->
                val exercise = state.exercises.firstOrNull { it.id == id }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(start = 13.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${exercise?.name ?: "Archived exercise"}", fontWeight = FontWeight.SemiBold)
                            exercise?.let { Text("${it.setCount} sets × ${it.repCount} reps  ·  ${it.currentWeightLb.prettyLb()} lb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { if (index > 0) selectedIds = selectedIds.swap(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { if (index < selectedIds.lastIndex) selectedIds = selectedIds.swap(index, index + 1) }, enabled = index < selectedIds.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { selectedIds = selectedIds - id }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove exercise")
                        }
                    }
                }
            }
        }
        Text("Add exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.exercises.isEmpty()) {
            Text("Create exercises first from the Plans screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.exercises.filter { it.id !in selectedIds }.forEach { exercise ->
                Card(Modifier.fillMaxWidth().clickable { selectedIds = selectedIds + exercise.id }) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(exercise.name, fontWeight = FontWeight.SemiBold)
                            Text("${exercise.setCount} sets × ${exercise.repCount} reps  ·  ${exercise.currentWeightLb.prettyLb()} lb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Button(onClick = {
            val id: String?
            val saved = if (existing == null) {
                id = viewModel.createWorkout(name)
                id != null
            } else {
                id = existing.id
                viewModel.updateWorkout(existing.id, name)
            }
            if (saved && id != null) {
                val originalIds = existing?.exercises?.map { it.exerciseId }?.toSet().orEmpty()
                selectedIds.filterNot { it in originalIds }.forEach { viewModel.addExercise(id, it) }
                originalIds.filterNot { it in selectedIds }.forEach { viewModel.removeExercise(id, it) }
                viewModel.reorderExercises(id, selectedIds)
                viewModel.assignDays(id, selectedDays)
                viewModel.navigate(AppScreen.PLANS)
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Save workout")
        }
        existing?.let {
            OutlinedButton(onClick = { if (viewModel.archiveWorkout(it.id)) viewModel.navigate(AppScreen.PLANS) }, modifier = Modifier.fillMaxWidth()) { Text("Archive workout") }
        }
    }
}

@Composable
private fun ActiveSessionScreen(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    val session = state.activeSession ?: return
    var sessionNote by remember(session.id) { mutableStateOf(session.note) }
    var showCancelConfirmation by remember(session.id) { mutableStateOf(false) }

    FormColumn {
        Text(session.workoutName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${session.exercises.count { it.outcome != null }} of ${session.exercises.size} exercises marked", color = MaterialTheme.colorScheme.onSurfaceVariant)
        session.exercises.sortedBy { it.position }.forEach { entry ->
            SessionExerciseCard(
                entry = entry,
                onEdit = { viewModel.navigate(AppScreen.EXERCISE_FORM, exerciseId = entry.performedExerciseId) },
                onMark = { outcome, note -> viewModel.markExercise(session.id, entry.plannedExerciseId, outcome, note) },
            )
        }
        OutlinedTextField(sessionNote, { sessionNote = it }, label = { Text("Workout notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { viewModel.finishSession(session.id, sessionNote) }, modifier = Modifier.fillMaxWidth()) {
            Text("Finish workout")
        }
        OutlinedButton(onClick = { showCancelConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel workout")
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancel workout?") },
            text = { Text("Your exercise results and workout note will be discarded. No weights will change.") },
            dismissButton = { TextButton(onClick = { showCancelConfirmation = false }) { Text("Keep working") } },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.cancelSession(session.id)) showCancelConfirmation = false
                }) { Text("Cancel workout") }
            },
        )
    }
}

@Composable
private fun SessionExerciseCard(
    entry: SessionExercise,
    onEdit: () -> Unit,
    onMark: (ExerciseOutcome, String) -> Unit,
) {
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(entry.performedExerciseName, fontWeight = FontWeight.Bold)
                    if (entry.performedExerciseId != entry.plannedExerciseId) {
                        Text("Planned: ${entry.plannedExerciseName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${entry.performedSetCount} sets × ${entry.performedRepCount} reps  ·  ${entry.performedWeightLb.prettyLb()} lb", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit exercise")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutcomeButton("Completed", entry.outcome == ExerciseOutcome.COMPLETED, MaterialTheme.colorScheme.primaryContainer) { onMark(ExerciseOutcome.COMPLETED, note) }
                OutcomeButton("Too hard", entry.outcome == ExerciseOutcome.TOO_HARD, Color(0xFFFFF1D8)) { onMark(ExerciseOutcome.TOO_HARD, note) }
                OutcomeButton("Not completed", entry.outcome == ExerciseOutcome.NOT_COMPLETED, MaterialTheme.colorScheme.surfaceVariant) { onMark(ExerciseOutcome.NOT_COMPLETED, note) }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Exercise notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        }
    }
}

@Composable
private fun RowScope.OutcomeButton(label: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp), colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = if (selected) selectedColor else Color.Transparent)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Set<DayOfWeek>.displayDays(): String = if (isEmpty()) "No days assigned" else sortedBy(DayOfWeek::getValue).joinToString(", ") { it.shortLabel() }

private fun DayOfWeek.shortLabel(): String = name.take(3).lowercase().replaceFirstChar(Char::uppercaseChar)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun <T> List<T>.swap(first: Int, second: Int): List<T> = toMutableList().also {
    val value = it[first]
    it[first] = it[second]
    it[second] = value
}

private fun Double.prettyLb(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.2f".format(this).trimEnd('0').trimEnd('.')
