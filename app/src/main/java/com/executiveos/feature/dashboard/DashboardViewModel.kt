package com.executiveos.feature.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.dashboard.Dashboard
import com.executiveos.domain.dashboard.DashboardCalculator
import com.executiveos.domain.dashboard.PlanningOverview
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.projects.ProjectTracking
import com.executiveos.domain.repository.DailyFocusRepository
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    settingsRepository: PrioritySettingsRepository,
    private val focusRepository: DailyFocusRepository,
    planningRepository: PlanningRepository,
    projectRepository: ProjectRepository,
    private val clock: Clock,
) : ViewModel() {
    private val messageChannel = Channel<Int>(Channel.BUFFERED)

    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    private val planning: Flow<PlanningOverview> = combine(
        planningRepository.observeBlocks(),
        planningRepository.capacity,
        projectRepository.observeProjects(),
    ) { blocks, capacity, projects ->
        PlanningOverview(
            blocks = blocks,
            capacity = capacity,
            delayedProjects = projects.count { details ->
                ProjectTracking.progress(
                    details.project,
                    details.milestones,
                    details.risks,
                    details.openTasks,
                    today(),
                ).isDelayed
            },
        )
    }

    val dashboard: StateFlow<Dashboard?> = combine(
        taskRepository.observeTaskList(),
        settingsRepository.settings,
        focusRepository.focus,
        planning,
    ) { items, settings, focus, overview ->
        val today = today()
        DashboardCalculator.build(
            TaskQueries.rank(items, settings, today),
            focus,
            today,
            clock.zone,
            overview,
            clock.instant(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
    )

    val zone: ZoneId get() = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    fun complete(taskId: String) = perform(R.string.tasks_done_completed) {
        taskRepository.setStatus(taskId, TaskStatus.COMPLETED)
    }

    fun pin(taskId: String) = perform(null) { focusRepository.pin(taskId, today()) }

    fun dismiss(taskId: String) = perform(null) { focusRepository.dismiss(taskId, today()) }

    fun resetFocus() = perform(null) { focusRepository.reset(today()) }

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
            if (message != null) messageChannel.send(message)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
