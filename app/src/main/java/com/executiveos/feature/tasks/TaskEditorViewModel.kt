package com.executiveos.feature.tasks

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.executiveos.R
import com.executiveos.core.navigation.TaskEditorRoute
import com.executiveos.core.navigation.TaskSection
import com.executiveos.domain.capture.MissingField
import com.executiveos.domain.capture.TaskCompleteness
import com.executiveos.domain.capture.TaskValidationError
import com.executiveos.domain.capture.TaskValidator
import com.executiveos.domain.model.AddDependencyResult
import com.executiveos.domain.model.Category
import com.executiveos.domain.model.ProjectSummary
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskHistoryEntry
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PriorityAssessment
import com.executiveos.domain.priority.PriorityEngine
import com.executiveos.domain.reminders.ReminderScheduler
import com.executiveos.domain.reminders.TaskReminder
import com.executiveos.domain.repository.CaptureDraftRepository
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.InboxRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ReminderRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveState { IDLE, SAVED, INVALID, FAILED }

data class TaskEditorUiState(
    val isLoading: Boolean = true,
    val task: Task? = null,
    val categories: List<Category> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val lastProjectId: String? = null,
    val missingFields: List<MissingField> = emptyList(),
    val errors: List<TaskValidationError> = emptyList(),
    val suggestSplitting: Boolean = false,
    val saveState: SaveState = SaveState.IDLE,
)

data class TaskRelationsUiState(
    val subtasks: List<TaskReference> = emptyList(),
    val dependencies: List<TaskReference> = emptyList(),
    val history: List<TaskHistoryEntry> = emptyList(),
    val dependencyCandidates: List<TaskReference> = emptyList(),
) {
    val hasOpenWork: Boolean
        get() = (subtasks + dependencies).any { TaskQueries.isOpen(it.status) }
}

sealed interface TaskEditorEvent {
    data object Close : TaskEditorEvent
    data class OpenTask(val taskId: String) : TaskEditorEvent
    data class Message(@StringRes val text: Int) : TaskEditorEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val inboxRepository: InboxRepository,
    private val draftRepository: CaptureDraftRepository,
    categoryRepository: CategoryRepository,
    prioritySettingsRepository: PrioritySettingsRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TaskEditorRoute>()
    private val task = MutableStateFlow<Task?>(null)
    private val isLoading = MutableStateFlow(true)
    private val saveState = MutableStateFlow(SaveState.IDLE)
    private val eventChannel = Channel<TaskEditorEvent>(Channel.BUFFERED)

    val events: Flow<TaskEditorEvent> = eventChannel.receiveAsFlow()

    val initialSection: TaskSection? =
        TaskSection.entries.firstOrNull { it.name == route.section }

    private val state = combine(task, isLoading, saveState) { current, loading, saved ->
        Triple(current, loading, saved)
    }

    val uiState: StateFlow<TaskEditorUiState> = combine(
        state,
        categoryRepository.observeCategories(),
        taskRepository.observeProjects(),
        draftRepository.lastProjectId,
    ) { (current, loading, saved), categories, projects, lastProject ->
        TaskEditorUiState(
            isLoading = loading,
            task = current,
            categories = categories,
            projects = projects,
            lastProjectId = lastProject,
            missingFields = current?.let(TaskCompleteness::missing).orEmpty(),
            errors = current?.let(TaskValidator::validate).orEmpty(),
            suggestSplitting = current?.let(TaskCompleteness::shouldSuggestSplitting) == true,
            saveState = saved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TaskEditorUiState(),
    )

    private val dependencies = taskRepository.observeDependencies(route.taskId)

    val relations: StateFlow<TaskRelationsUiState> = combine(
        taskRepository.observeSubtasks(route.taskId),
        dependencies,
        taskRepository.observeHistory(route.taskId),
        taskRepository.observeTaskList(),
    ) { subtasks, currentDependencies, history, allTasks ->
        val alreadyLinked = currentDependencies.map { it.id }.toSet() + route.taskId
        TaskRelationsUiState(
            subtasks = subtasks,
            dependencies = currentDependencies,
            history = history,
            dependencyCandidates = allTasks
                .filter { TaskQueries.isOpen(it.task.status) && it.task.id !in alreadyLinked }
                .map { TaskReference(it.task.id, it.task.title, it.task.status) }
                .sortedBy { it.title.lowercase() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TaskRelationsUiState(),
    )

    val priority: StateFlow<PriorityAssessment?> = combine(
        task,
        prioritySettingsRepository.settings,
        dependencies,
    ) { current, settings, currentDependencies ->
        current?.let {
            PriorityEngine.assess(
                task = it,
                settings = settings,
                today = LocalDate.now(clock),
                openDependencies = currentDependencies.count { dependency ->
                    TaskQueries.isOpen(dependency.status)
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            task.value = taskRepository.observeTask(route.taskId).first()
            isLoading.value = false
        }
        viewModelScope.launch {
            task.filterNotNull()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MILLIS)
                .collect { saveIfValid(it) }
        }
    }

    fun onEdit(transform: Task.() -> Task) {
        task.update { current -> current?.transform() }
    }

    fun onProjectSelected(projectId: String?) {
        onEdit { copy(projectId = projectId) }
        viewModelScope.launch { draftRepository.rememberProject(projectId) }
    }

    fun flush(onComplete: () -> Unit) {
        viewModelScope.launch {
            task.value?.let { saveIfValid(it) }
            onComplete()
        }
    }

    fun setStatus(status: TaskStatus) = perform {
        persistCurrent()
        taskRepository.setStatus(route.taskId, status)
        onEdit { copy(status = status) }
    }

    fun archive() = perform {
        persistCurrent()
        taskRepository.setStatus(route.taskId, TaskStatus.ARCHIVED)
        eventChannel.send(TaskEditorEvent.Close)
    }

    fun duplicate() = perform {
        persistCurrent()
        eventChannel.send(TaskEditorEvent.OpenTask(taskRepository.duplicate(route.taskId)))
    }

    fun delegate(person: String) = perform {
        persistCurrent()
        taskRepository.delegate(route.taskId, person)
        task.value = taskRepository.observeTask(route.taskId).first()
    }

    fun postpone(until: LocalDate) = perform {
        persistCurrent()
        taskRepository.postpone(route.taskId, until)
        onEdit { copy(targetDate = until) }
    }

    val reminder: StateFlow<TaskReminder?> = reminderRepository.observeTaskReminder(route.taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setReminder(at: Instant) = perform {
        if (at.isAfter(clock.instant())) {
            reminderScheduler.scheduleTaskReminder(
                reminderRepository.setTaskReminder(route.taskId, at),
                at,
            )
        } else {
            eventChannel.send(TaskEditorEvent.Message(R.string.reminder_in_past))
        }
    }

    fun clearReminder() = perform {
        reminderRepository.clearTaskReminder(route.taskId)
            ?.let(reminderScheduler::cancelTaskReminder)
    }

    fun addSubtask(title: String) = perform { taskRepository.create(title, route.taskId) }

    fun addDependency(dependsOnId: String) = perform {
        val message = when (taskRepository.addDependency(route.taskId, dependsOnId)) {
            AddDependencyResult.ADDED -> null
            AddDependencyResult.ALREADY_EXISTS -> R.string.dependency_exists
            AddDependencyResult.WOULD_CREATE_CYCLE -> R.string.dependency_cycle
        }
        if (message != null) eventChannel.send(TaskEditorEvent.Message(message))
    }

    fun removeDependency(dependsOnId: String) =
        perform { taskRepository.removeDependency(route.taskId, dependsOnId) }

    fun convertToProject() {
        val itemId = task.value?.sourceInboxItemId ?: return
        perform {
            persistCurrent()
            inboxRepository.convertToProject(itemId)
            eventChannel.send(TaskEditorEvent.Close)
        }
    }

    private suspend fun persistCurrent() {
        task.value?.let { saveIfValid(it) }
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                eventChannel.send(TaskEditorEvent.Message(R.string.tasks_action_failed))
            }
        }
    }

    private suspend fun saveIfValid(current: Task) {
        if (TaskValidator.validate(current).isNotEmpty()) {
            saveState.value = SaveState.INVALID
            return
        }
        saveState.value = try {
            taskRepository.save(current)
            SaveState.SAVED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SaveState.FAILED
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
    }
}
