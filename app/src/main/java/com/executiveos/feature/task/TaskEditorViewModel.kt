package com.executiveos.feature.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.executiveos.core.navigation.TaskEditorRoute
import com.executiveos.core.navigation.TaskSection
import com.executiveos.domain.capture.MissingField
import com.executiveos.domain.capture.TaskCompleteness
import com.executiveos.domain.capture.TaskValidationError
import com.executiveos.domain.capture.TaskValidator
import com.executiveos.domain.model.Category
import com.executiveos.domain.model.ProjectSummary
import com.executiveos.domain.model.Task
import com.executiveos.domain.repository.CaptureDraftRepository
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.InboxRepository
import com.executiveos.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

@OptIn(FlowPreview::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val inboxRepository: InboxRepository,
    private val draftRepository: CaptureDraftRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TaskEditorRoute>()
    private val task = MutableStateFlow<Task?>(null)
    private val isLoading = MutableStateFlow(true)
    private val saveState = MutableStateFlow(SaveState.IDLE)

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

    fun convertToProject(onComplete: () -> Unit) {
        val itemId = task.value?.sourceInboxItemId ?: return
        viewModelScope.launch {
            task.value?.let { saveIfValid(it) }
            inboxRepository.convertToProject(itemId)
            onComplete()
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
