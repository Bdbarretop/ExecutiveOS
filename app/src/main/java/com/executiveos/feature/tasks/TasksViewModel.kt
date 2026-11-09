package com.executiveos.feature.tasks

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskFilters
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskView
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilterChoice(val value: String, val label: String)

data class FilterOptions(
    val categories: List<FilterChoice> = emptyList(),
    val projects: List<FilterChoice> = emptyList(),
    val owners: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

data class TasksUiState(
    val isLoading: Boolean = true,
    val view: TaskView = TaskView.ALL,
    val query: String = "",
    val filters: TaskFilters = TaskFilters(),
    val tasks: List<RankedTask> = emptyList(),
    val options: FilterOptions = FilterOptions(),
)

sealed interface TasksEvent {
    data class OpenTask(val taskId: String) : TasksEvent
    data class Message(@StringRes val text: Int) : TasksEvent
}

private data class Selection(val view: TaskView, val filters: TaskFilters, val query: String)

@OptIn(FlowPreview::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    settingsRepository: PrioritySettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Inicio puede abrir directamente la vista de un indicador o una alerta. */
    private val view = MutableStateFlow(
        TaskView.entries.firstOrNull { it.name == savedStateHandle.get<String>(VIEW_ARGUMENT) }
            ?: TaskView.ALL,
    )
    private val filters = MutableStateFlow(TaskFilters())
    private val query = MutableStateFlow("")
    private val eventChannel = Channel<TasksEvent>(Channel.BUFFERED)

    val events: Flow<TasksEvent> = eventChannel.receiveAsFlow()

    private val ranked = combine(
        repository.observeTaskList(),
        settingsRepository.settings,
    ) { items, settings -> TaskQueries.rank(items, settings, today()) }

    private val selection = combine(
        view,
        filters,
        query.debounce(SEARCH_DEBOUNCE_MILLIS),
    ) { currentView, currentFilters, currentQuery ->
        Selection(currentView, currentFilters, currentQuery)
    }

    val uiState: StateFlow<TasksUiState> = combine(
        ranked,
        selection,
        query,
    ) { allTasks, current, typedQuery ->
        TasksUiState(
            isLoading = false,
            view = current.view,
            query = typedQuery,
            filters = current.filters,
            tasks = TaskQueries.select(
                allTasks,
                current.view,
                current.filters,
                current.query,
                today(),
            ),
            options = optionsFrom(allTasks),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TasksUiState(),
    )

    fun onViewSelected(selected: TaskView) {
        view.value = selected
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onFiltersChange(transform: TaskFilters.() -> TaskFilters) {
        filters.update { it.transform() }
    }

    fun clearFilters() {
        filters.value = TaskFilters()
    }

    fun create(title: String) = perform(null) {
        eventChannel.send(TasksEvent.OpenTask(repository.create(title)))
    }

    fun complete(taskId: String) =
        perform(R.string.tasks_done_completed) { repository.setStatus(taskId, TaskStatus.COMPLETED) }

    fun setInProgress(taskId: String) =
        perform(R.string.tasks_done_updated) { repository.setStatus(taskId, TaskStatus.IN_PROGRESS) }

    fun archive(taskId: String) =
        perform(R.string.tasks_done_archived) { repository.setStatus(taskId, TaskStatus.ARCHIVED) }

    fun duplicate(taskId: String) =
        perform(R.string.tasks_done_duplicated) { repository.duplicate(taskId) }

    private fun perform(@StringRes success: Int?, action: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                action()
                success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                R.string.tasks_action_failed
            }
            if (message != null) eventChannel.send(TasksEvent.Message(message))
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun optionsFrom(tasks: List<RankedTask>): FilterOptions {
        val items = tasks.map { it.item }
        return FilterOptions(
            categories = items
                .mapNotNull { item ->
                    val id = item.task.categoryId ?: return@mapNotNull null
                    item.categoryName?.let { FilterChoice(id, it) }
                }
                .distinct()
                .sortedBy { it.label },
            projects = items
                .mapNotNull { item ->
                    val id = item.task.projectId ?: return@mapNotNull null
                    item.projectName?.let { FilterChoice(id, it) }
                }
                .distinct()
                .sortedBy { it.label },
            owners = items.mapNotNull { it.task.owner }.distinct().sorted(),
            tags = items.flatMap { it.task.tags }.distinct().sorted(),
        )
    }

    private companion object {
        const val VIEW_ARGUMENT = "view"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 150L
    }
}
