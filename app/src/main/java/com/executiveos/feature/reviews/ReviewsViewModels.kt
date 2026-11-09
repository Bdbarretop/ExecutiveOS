package com.executiveos.feature.reviews

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.domain.learning.LearningGoalStatus
import com.executiveos.domain.planning.PlanningPeriod
import com.executiveos.domain.planning.PlanningRules
import com.executiveos.domain.projects.ProjectTracking
import com.executiveos.domain.repository.LearningRepository
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.ReviewRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.reviews.Review
import com.executiveos.domain.reviews.ReviewQuestion
import com.executiveos.domain.reviews.ReviewRules
import com.executiveos.domain.reviews.ReviewType
import com.executiveos.domain.reviews.WeeklySummary
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.feature.tasks.SaveState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val AUTOSAVE_DEBOUNCE_MILLIS = 500L

data class ReviewsUiState(
    val today: LocalDate,
    val pendingTypes: Set<ReviewType>,
    val history: List<Review>,
)

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    repository: ReviewRepository,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<ReviewsUiState?> = repository.observeHistory()
        .map { all ->
            val today = LocalDate.now(clock)
            val answered = all.filter { it.answers.isNotEmpty() }
            ReviewsUiState(
                today = today,
                pendingTypes = ReviewType.entries.filterNot { type ->
                    answered.any { it.type == type && it.periodDate == ReviewRules.periodDate(type, today) }
                }.toSet(),
                history = answered,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ReviewEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reviewRepository: ReviewRepository,
    taskRepository: TaskRepository,
    prioritySettingsRepository: PrioritySettingsRepository,
    projectRepository: ProjectRepository,
    planningRepository: PlanningRepository,
    learningRepository: LearningRepository,
    private val clock: Clock,
) : ViewModel() {
    val type: ReviewType = ReviewType.valueOf(checkNotNull(savedStateHandle["type"]))
    private val date: LocalDate =
        savedStateHandle.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(clock)
    val periodDate: LocalDate = ReviewRules.periodDate(type, date)
    val questions: List<ReviewQuestion> = ReviewRules.questions(type)

    private val mutableAnswers = MutableStateFlow<Map<ReviewQuestion, String>?>(null)
    private val mutableSaveState = MutableStateFlow(SaveState.IDLE)
    private var lastSaved: Map<ReviewQuestion, String> = emptyMap()

    val answers: StateFlow<Map<ReviewQuestion, String>?> = mutableAnswers.asStateFlow()
    val saveState: StateFlow<SaveState> = mutableSaveState.asStateFlow()

    private val rankedTasks = combine(
        taskRepository.observeTaskList(),
        prioritySettingsRepository.settings,
    ) { items, settings -> TaskQueries.rank(items, settings, LocalDate.now(clock)) }

    private val planning = combine(
        planningRepository.observeBlocks(),
        planningRepository.capacity,
    ) { blocks, capacity -> blocks to capacity }

    private val learning = combine(
        learningRepository.observeSessions(),
        learningRepository.observeGoals(),
    ) { sessions, goals -> sessions to goals }

    val summary: StateFlow<WeeklySummary?> = if (type == ReviewType.WEEKLY) {
        combine(
            rankedTasks,
            projectRepository.observeProjects(),
            planning,
            learning,
        ) { tasks, projects, (blocks, capacity), (sessions, goals) ->
            val today = LocalDate.now(clock)
            ReviewRules.summarize(
                tasks = tasks,
                projects = projects.map { details ->
                    details.project to ProjectTracking.progress(
                        details.project,
                        details.milestones,
                        details.risks,
                        details.openTasks,
                        today,
                    )
                },
                blocks = blocks,
                capacity = capacity,
                sessions = sessions,
                hasActiveGoals = goals.any { it.status == LearningGoalStatus.ACTIVE },
                week = PlanningRules.span(PlanningPeriod.WEEK, periodDate),
                today = today,
                zone = clock.zone,
            )
        }
    } else {
        flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        viewModelScope.launch {
            val existing = reviewRepository.observeReview(type, date).first()?.answers.orEmpty()
            lastSaved = existing
            mutableAnswers.value = existing
        }
        viewModelScope.launch {
            mutableAnswers.filterNotNull()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MILLIS)
                .collect { persist(it) }
        }
    }

    fun onAnswerChange(question: ReviewQuestion, text: String) {
        mutableAnswers.update { current ->
            current?.let { if (text.isEmpty()) it - question else it + (question to text) }
        }
    }

    fun flush(onComplete: () -> Unit) {
        viewModelScope.launch {
            mutableAnswers.value?.let { persist(it) }
            onComplete()
        }
    }

    /** Abrir una revisión y salir sin escribir no debe crear una revisión vacía. */
    private suspend fun persist(current: Map<ReviewQuestion, String>) {
        if (current == lastSaved) return
        mutableSaveState.value = try {
            reviewRepository.saveAnswers(type, date, current)
            lastSaved = current
            SaveState.SAVED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SaveState.FAILED
        }
    }
}
