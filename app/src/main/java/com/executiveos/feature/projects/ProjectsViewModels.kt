package com.executiveos.feature.projects

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.projects.MilestoneKind
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectMilestone
import com.executiveos.domain.projects.ProjectProgress
import com.executiveos.domain.projects.ProjectRisk
import com.executiveos.domain.projects.ProjectTracking
import com.executiveos.domain.projects.ProjectValidationError
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.feature.tasks.SaveState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L

data class ProjectListItem(val project: Project, val progress: ProjectProgress)

sealed interface ProjectsEvent {
    data class OpenProject(val projectId: String) : ProjectsEvent
    data class Message(@StringRes val text: Int) : ProjectsEvent
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val clock: Clock,
) : ViewModel() {
    private val eventChannel = Channel<ProjectsEvent>(Channel.BUFFERED)

    val events: Flow<ProjectsEvent> = eventChannel.receiveAsFlow()

    val projects: StateFlow<List<ProjectListItem>?> = repository.observeProjects()
        .map { all ->
            val today = LocalDate.now(clock)
            all.map { details ->
                ProjectListItem(
                    details.project,
                    ProjectTracking.progress(
                        details.project,
                        details.milestones,
                        details.risks,
                        details.openTasks,
                        today,
                    ),
                )
            }.sortedWith(LIST_ORDER)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun create(name: String) {
        viewModelScope.launch {
            val event = try {
                ProjectsEvent.OpenProject(repository.create(name))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectsEvent.Message(R.string.tasks_action_failed)
            }
            eventChannel.send(event)
        }
    }

    private companion object {
        /** Lo terminado va al final; lo retrasado, primero, y después lo que vence antes. */
        val LIST_ORDER = compareBy<ProjectListItem>(
            { ProjectTracking.isFinished(it.project.status) },
            { !it.progress.isDelayed },
            { it.project.targetDate ?: LocalDate.MAX },
            { it.project.name.lowercase() },
        )
    }
}

data class ProjectEditorUiState(
    val isLoading: Boolean = true,
    val project: Project? = null,
    val milestones: List<ProjectMilestone> = emptyList(),
    val risks: List<ProjectRisk> = emptyList(),
    val tasks: List<TaskReference> = emptyList(),
    val progress: ProjectProgress? = null,
    val errors: List<ProjectValidationError> = emptyList(),
    val saveState: SaveState = SaveState.IDLE,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    private val projectId: String = checkNotNull(savedStateHandle["projectId"])
    private val project = MutableStateFlow<Project?>(null)
    private val isLoading = MutableStateFlow(true)
    private val saveState = MutableStateFlow(SaveState.IDLE)
    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    private var lastSaved: Project? = null

    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    private val linkedTasks = taskRepository.observeTaskList().map { items ->
        items.filter { it.task.projectId == projectId }
            .map { TaskReference(it.task.id, it.task.title, it.task.status) }
            .sortedWith(compareBy({ !TaskQueries.isOpen(it.status) }, { it.title.lowercase() }))
    }

    val uiState: StateFlow<ProjectEditorUiState> = combine(
        project,
        isLoading,
        saveState,
        repository.observeProject(projectId),
        linkedTasks,
    ) { current, loading, saved, tracked, tasks ->
        ProjectEditorUiState(
            isLoading = loading,
            project = current,
            milestones = tracked?.milestones.orEmpty(),
            risks = tracked?.risks.orEmpty(),
            tasks = tasks,
            progress = if (current != null && tracked != null) {
                ProjectTracking.progress(
                    current,
                    tracked.milestones,
                    tracked.risks,
                    tracked.openTasks,
                    LocalDate.now(clock),
                )
            } else {
                null
            },
            errors = current?.let(ProjectTracking::validate).orEmpty(),
            saveState = saved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProjectEditorUiState(),
    )

    init {
        viewModelScope.launch {
            val loaded = repository.observeProject(projectId).first()?.project
            lastSaved = loaded
            project.value = loaded
            isLoading.value = false
        }
        viewModelScope.launch {
            project.filterNotNull()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MILLIS)
                .collect { saveIfValid(it) }
        }
    }

    fun onEdit(transform: Project.() -> Project) {
        project.update { current -> current?.transform() }
    }

    fun flush(onComplete: () -> Unit) {
        viewModelScope.launch {
            project.value?.let { saveIfValid(it) }
            onComplete()
        }
    }

    fun addMilestone(name: String, kind: MilestoneKind, weight: Int, dueDate: LocalDate?) =
        perform { repository.addMilestone(projectId, name, kind, weight, dueDate) }

    fun setMilestoneCompleted(milestoneId: String, completed: Boolean) =
        perform { repository.setMilestoneCompleted(milestoneId, completed) }

    fun deleteMilestone(milestoneId: String) = perform { repository.deleteMilestone(milestoneId) }

    fun addRisk(description: String, mitigation: String?, severity: Int) =
        perform { repository.addRisk(projectId, description, mitigation, severity) }

    fun setRiskOpen(riskId: String, isOpen: Boolean) =
        perform { repository.setRiskOpen(riskId, isOpen) }

    fun addTask(title: String) = perform { taskRepository.create(title, projectId = projectId) }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                messageChannel.send(R.string.tasks_action_failed)
            }
        }
    }

    private suspend fun saveIfValid(current: Project) {
        if (ProjectTracking.validate(current).isNotEmpty()) {
            saveState.value = SaveState.INVALID
            return
        }
        if (current == lastSaved) return
        saveState.value = try {
            repository.save(current)
            lastSaved = current
            SaveState.SAVED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SaveState.FAILED
        }
    }
}
