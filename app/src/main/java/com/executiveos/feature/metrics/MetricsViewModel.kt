package com.executiveos.feature.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.domain.metrics.Metrics
import com.executiveos.domain.metrics.MetricsCalculator
import com.executiveos.domain.metrics.ProjectProgressLine
import com.executiveos.domain.model.Category
import com.executiveos.domain.projects.ProjectTracking
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.LearningRepository
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val STOP_TIMEOUT_MILLIS = 5_000L

data class MetricsUiState(
    val period: MetricPeriod,
    val categories: List<Category>,
    val categoryId: String?,
    val metrics: Metrics,
)

private data class MetricsFilters(
    val period: MetricPeriod,
    val anchor: LocalDate,
    val categoryId: String?,
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    taskRepository: TaskRepository,
    prioritySettingsRepository: PrioritySettingsRepository,
    planningRepository: PlanningRepository,
    learningRepository: LearningRepository,
    projectRepository: ProjectRepository,
    categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val period = MutableStateFlow(MetricPeriod.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now(clock))
    private val categoryId = MutableStateFlow<String?>(null)

    private val filters = combine(period, anchor, categoryId, ::MetricsFilters)

    private val tasks = combine(
        taskRepository.observeTaskList(),
        prioritySettingsRepository.settings,
    ) { items, settings -> TaskQueries.rank(items, settings, LocalDate.now(clock)) }

    private val projects = projectRepository.observeProjects().map { all ->
        all.filterNot { ProjectTracking.isFinished(it.project.status) }.map { details ->
            val progress = ProjectTracking.progress(
                details.project,
                details.milestones,
                details.risks,
                details.openTasks,
                LocalDate.now(clock),
            )
            ProjectProgressLine(details.project.name, progress.percent, progress.isDelayed)
        }
    }

    private val activity = combine(
        planningRepository.observeBlocks(),
        learningRepository.observeSessions(),
        projects,
    ) { blocks, sessions, lines -> Triple(blocks, sessions, lines) }

    val uiState: StateFlow<MetricsUiState?> = combine(
        filters,
        tasks,
        activity,
        categoryRepository.observeCategories(),
    ) { selected, ranked, (blocks, sessions, lines), categories ->
        MetricsUiState(
            period = selected.period,
            categories = categories,
            categoryId = selected.categoryId,
            metrics = MetricsCalculator.calculate(
                tasks = ranked,
                blocks = blocks,
                sessions = sessions,
                projects = lines,
                span = MetricsCalculator.span(selected.period, selected.anchor),
                categoryId = selected.categoryId,
                today = LocalDate.now(clock),
                zone = clock.zone,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun selectPeriod(selected: MetricPeriod) {
        period.value = selected
    }

    fun selectCategory(selected: String?) {
        categoryId.value = selected
    }

    fun previous() = anchor.update { MetricsCalculator.shift(period.value, it, -1) }

    fun next() = anchor.update { MetricsCalculator.shift(period.value, it, 1) }
}
