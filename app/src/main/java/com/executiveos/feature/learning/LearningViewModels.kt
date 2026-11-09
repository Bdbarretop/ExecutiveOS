package com.executiveos.feature.learning

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.learning.LearningGoal
import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.LearningStats
import com.executiveos.domain.learning.LearningValidationError
import com.executiveos.domain.learning.TopicMinutes
import com.executiveos.domain.learning.WeekMinutes
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.domain.metrics.MetricsCalculator
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.repository.LearningRepository
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

data class GoalListItem(val goal: LearningGoal, val totalMinutes: Int)

data class LearningUiState(
    val period: MetricPeriod,
    val span: DateSpan,
    val totalMinutes: Int,
    val byTopic: List<TopicMinutes>,
    val byWeek: List<WeekMinutes>,
    val goals: List<GoalListItem>,
)

sealed interface LearningEvent {
    data class OpenGoal(val goalId: String) : LearningEvent
    data class Message(@StringRes val text: Int) : LearningEvent
}

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val clock: Clock,
) : ViewModel() {
    private val period = MutableStateFlow(MetricPeriod.WEEK)
    private val eventChannel = Channel<LearningEvent>(Channel.BUFFERED)

    val events: Flow<LearningEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<LearningUiState?> = combine(
        period,
        repository.observeGoals(),
        repository.observeSessions(),
    ) { current, goals, sessions ->
        val span = MetricsCalculator.span(current, LocalDate.now(clock))
        LearningUiState(
            period = current,
            span = span,
            totalMinutes = LearningStats.minutesIn(sessions, span),
            byTopic = LearningStats.byTopic(goals, sessions, span),
            byWeek = LearningStats.byWeek(sessions, span),
            goals = goals.map { goal ->
                GoalListItem(goal, sessions.filter { it.goalId == goal.id }.sumOf { it.minutes })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun selectPeriod(selected: MetricPeriod) {
        period.value = selected
    }

    fun createGoal(title: String, topic: String) {
        viewModelScope.launch {
            val event = try {
                LearningEvent.OpenGoal(repository.createGoal(title, topic))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                LearningEvent.Message(R.string.tasks_action_failed)
            }
            eventChannel.send(event)
        }
    }
}

data class LearningGoalUiState(
    val isLoading: Boolean = true,
    val goal: LearningGoal? = null,
    val sessions: List<LearningSession> = emptyList(),
    val errors: List<LearningValidationError> = emptyList(),
    val saveState: SaveState = SaveState.IDLE,
) {
    val totalMinutes: Int get() = sessions.sumOf { it.minutes }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LearningGoalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LearningRepository,
    private val clock: Clock,
) : ViewModel() {
    private val goalId: String = checkNotNull(savedStateHandle["goalId"])
    private val goal = MutableStateFlow<LearningGoal?>(null)
    private val isLoading = MutableStateFlow(true)
    private val saveState = MutableStateFlow(SaveState.IDLE)
    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    private var lastSaved: LearningGoal? = null

    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<LearningGoalUiState> = combine(
        goal,
        isLoading,
        saveState,
        repository.observeSessions().map { all -> all.filter { it.goalId == goalId } },
    ) { current, loading, saved, sessions ->
        LearningGoalUiState(
            isLoading = loading,
            goal = current,
            sessions = sessions,
            errors = current?.let(LearningStats::validate).orEmpty(),
            saveState = saved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LearningGoalUiState(),
    )

    init {
        viewModelScope.launch {
            val loaded = repository.observeGoals().first().firstOrNull { it.id == goalId }
            lastSaved = loaded
            goal.value = loaded
            isLoading.value = false
        }
        viewModelScope.launch {
            goal.filterNotNull()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MILLIS)
                .collect { saveIfValid(it) }
        }
    }

    fun today(): LocalDate = LocalDate.now(clock)

    fun onEdit(transform: LearningGoal.() -> LearningGoal) {
        goal.update { current -> current?.transform() }
    }

    fun flush(onComplete: () -> Unit) {
        viewModelScope.launch {
            goal.value?.let { saveIfValid(it) }
            onComplete()
        }
    }

    fun addSession(date: LocalDate, minutes: Int, notes: String?, evaluation: String?) =
        perform { repository.addSession(goalId, date, minutes, notes, evaluation) }

    fun deleteSession(sessionId: String) = perform { repository.deleteSession(sessionId) }

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

    private suspend fun saveIfValid(current: LearningGoal) {
        if (LearningStats.validate(current).isNotEmpty()) {
            saveState.value = SaveState.INVALID
            return
        }
        if (current == lastSaved) return
        saveState.value = try {
            repository.saveGoal(current)
            lastSaved = current
            SaveState.SAVED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SaveState.FAILED
        }
    }
}
