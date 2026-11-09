package com.executiveos.feature.planning

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.BlockValidationError
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.CapacitySummary
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.planning.ExecutionByType
import com.executiveos.domain.planning.PlanningPeriod
import com.executiveos.domain.planning.PlanningRules
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val LAST_DEFAULT_START_HOUR = 22
private val DEFAULT_START: LocalTime = LocalTime.of(9, 0)

data class DayPlan(
    val date: LocalDate,
    val blocks: List<TimeBlock>,
    val plannedMinutes: Int,
    val isOverloaded: Boolean,
)

data class PlanningUiState(
    val period: PlanningPeriod,
    val anchor: LocalDate,
    val today: LocalDate,
    val span: DateSpan,
    val days: List<DayPlan>,
    val capacity: CapacitySummary,
    val execution: List<ExecutionByType>,
    val overlappingIds: Set<String>,
    val taskTitles: Map<String, String>,
    val settings: CapacitySettings,
    val zone: ZoneId,
)

@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val repository: PlanningRepository,
    taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    private val period = MutableStateFlow(PlanningPeriod.DAY)
    private val anchor = MutableStateFlow(LocalDate.now(clock))
    private val messageChannel = Channel<Int>(Channel.BUFFERED)

    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<PlanningUiState?> = combine(
        period,
        anchor,
        repository.observeBlocks(),
        repository.capacity,
        taskRepository.observeTaskList().map { items -> items.associate { it.task.id to it.task.title } },
    ) { currentPeriod, currentAnchor, blocks, settings, titles ->
        val zone = clock.zone
        val span = PlanningRules.span(currentPeriod, currentAnchor)
        val days = span.days.map { day ->
            val summary = PlanningRules.capacity(blocks, day, day, settings, zone)
            DayPlan(
                date = day,
                blocks = PlanningRules.onDay(blocks, day, zone),
                plannedMinutes = summary.plannedMinutes,
                isOverloaded = summary.isOverloaded,
            )
        }
        PlanningUiState(
            period = currentPeriod,
            anchor = currentAnchor,
            today = LocalDate.now(clock),
            span = span,
            days = days,
            capacity = PlanningRules.capacity(blocks, span.from, span.to, settings, zone),
            execution = PlanningRules.execution(blocks, span, zone),
            overlappingIds = PlanningRules.overlappingIds(days.flatMap { it.blocks }),
            taskTitles = titles,
            settings = settings,
            zone = zone,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
    )

    fun selectPeriod(selected: PlanningPeriod) {
        period.value = selected
    }

    fun previous() = anchor.update { PlanningRules.shift(period.value, it, -1) }

    fun next() = anchor.update { PlanningRules.shift(period.value, it, 1) }

    fun goToToday() {
        anchor.value = LocalDate.now(clock)
    }

    fun openDay(date: LocalDate) {
        anchor.value = date
        period.value = PlanningPeriod.DAY
    }

    fun move(block: TimeBlock, days: Long, minutes: Long) = perform(R.string.block_moved) {
        repository.save(PlanningRules.move(block, days, minutes, clock.zone))
    }

    fun logActual(block: TimeBlock, minutes: Int?) = perform(R.string.task_saved) {
        repository.save(block.copy(actualMinutes = minutes))
    }

    fun delete(blockId: String) = perform(R.string.block_deleted) { repository.delete(blockId) }

    fun saveCapacity(settings: CapacitySettings) = perform(R.string.task_saved) {
        repository.saveCapacity(settings)
    }

    private fun perform(@StringRes success: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                action()
                success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                R.string.tasks_action_failed
            }
            messageChannel.send(message)
        }
    }
}

data class BlockDraft(
    val id: String,
    val type: BlockType,
    val title: String,
    val taskId: String?,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val actualMinutes: Int?,
) {
    fun toBlock(zone: ZoneId) = TimeBlock(
        id = id,
        type = type,
        title = title,
        taskId = taskId,
        startAt = date.atTime(start).atZone(zone).toInstant(),
        endAt = date.atTime(end).atZone(zone).toInstant(),
        actualMinutes = actualMinutes,
    )
}

data class BlockEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: BlockDraft? = null,
    val taskTitle: String? = null,
    val tasks: List<TaskReference> = emptyList(),
    val errors: List<BlockValidationError> = emptyList(),
    val showTitleError: Boolean = false,
    val conflicts: List<TimeBlock> = emptyList(),
    val exceedsTarget: Boolean = false,
)

sealed interface BlockEditorEvent {
    data object Close : BlockEditorEvent
    data class Message(@StringRes val text: Int) : BlockEditorEvent
}

@HiltViewModel
class BlockEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlanningRepository,
    taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    private val blockId: String? = savedStateHandle["blockId"]
    private val requestedDate: LocalDate? =
        savedStateHandle.get<String>("date")?.let(LocalDate::parse)
    private val draft = MutableStateFlow<BlockDraft?>(null)
    private val isLoading = MutableStateFlow(true)
    private val titleErrorRequested = MutableStateFlow(false)
    private val eventChannel = Channel<BlockEditorEvent>(Channel.BUFFERED)

    val events: Flow<BlockEditorEvent> = eventChannel.receiveAsFlow()

    private val allTasks = taskRepository.observeTaskList()

    private val planning = combine(repository.observeBlocks(), repository.capacity) { blocks, settings ->
        blocks to settings
    }

    private val editorFlags = combine(isLoading, titleErrorRequested) { loading, titleError ->
        loading to titleError
    }

    val uiState: StateFlow<BlockEditorUiState> = combine(
        draft,
        editorFlags,
        planning,
        allTasks,
    ) { current, (loading, titleError), (blocks, settings), tasks ->
        val candidate = current?.toBlock(clock.zone)
        val errors = candidate?.let(PlanningRules::validate).orEmpty()
        val timesAreValid = candidate != null && BlockValidationError.END_NOT_AFTER_START !in errors
        val others = blocks.filter { it.id != current?.id }
        BlockEditorUiState(
            isLoading = loading,
            isNew = blockId == null,
            draft = current,
            taskTitle = tasks.firstOrNull { it.task.id == current?.taskId }?.task?.title,
            tasks = tasks
                .filter { TaskQueries.isOpen(it.task.status) }
                .map { TaskReference(it.task.id, it.task.title, it.task.status) }
                .sortedBy { it.title.lowercase() },
            errors = errors,
            showTitleError = titleError && BlockValidationError.BLANK_TITLE in errors,
            conflicts = if (timesAreValid) PlanningRules.conflicts(candidate!!, others) else emptyList(),
            exceedsTarget = timesAreValid &&
                candidate!!.type != BlockType.PERSONAL &&
                PlanningRules.capacity(
                    others + candidate,
                    current.date,
                    current.date,
                    settings,
                    clock.zone,
                ).isOverloaded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BlockEditorUiState(),
    )

    init {
        viewModelScope.launch {
            draft.value = if (blockId == null) {
                newDraft()
            } else {
                repository.observeBlocks().first().firstOrNull { it.id == blockId }?.toDraft()
            }
            isLoading.value = false
        }
    }

    private fun newDraft(): BlockDraft {
        val today = LocalDate.now(clock)
        val date = requestedDate ?: today
        val start = if (date == today) nextFullHour() else DEFAULT_START
        return BlockDraft(
            id = UUID.randomUUID().toString(),
            type = BlockType.DEEP_WORK,
            title = "",
            taskId = null,
            date = date,
            start = start,
            end = start.plusHours(1),
            actualMinutes = null,
        )
    }

    /** Sin pasar de la medianoche: un bloque nuevo debe poder guardarse tal como se propone. */
    private fun nextFullHour(): LocalTime {
        val now = LocalTime.now(clock)
        return if (now.hour >= LAST_DEFAULT_START_HOUR) {
            LocalTime.of(LAST_DEFAULT_START_HOUR, 0)
        } else {
            now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        }
    }

    private fun TimeBlock.toDraft() = BlockDraft(
        id = id,
        type = type,
        title = title,
        taskId = taskId,
        date = startAt.atZone(clock.zone).toLocalDate(),
        start = startAt.atZone(clock.zone).toLocalTime(),
        end = endAt.atZone(clock.zone).toLocalTime(),
        actualMinutes = actualMinutes,
    )

    fun onEdit(transform: BlockDraft.() -> BlockDraft) {
        draft.update { current -> current?.transform() }
    }

    /** Al mover el inicio, el bloque conserva su duración si cabe en el mismo día. */
    fun onStartSelected(time: LocalTime) = onEdit {
        val shifted = time.plus(Duration.between(start, end))
        copy(start = time, end = if (shifted.isAfter(time)) shifted else end)
    }

    fun onTaskSelected(task: TaskReference?) = onEdit {
        copy(taskId = task?.id, title = title.ifBlank { task?.title.orEmpty() })
    }

    fun save() {
        val block = draft.value?.toBlock(clock.zone) ?: return
        if (PlanningRules.validate(block).isNotEmpty()) {
            titleErrorRequested.value = true
            return
        }
        perform {
            repository.save(block)
            eventChannel.send(BlockEditorEvent.Close)
        }
    }

    fun delete() {
        val id = draft.value?.id ?: return
        perform {
            repository.delete(id)
            eventChannel.send(BlockEditorEvent.Close)
        }
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                eventChannel.send(BlockEditorEvent.Message(R.string.tasks_action_failed))
            }
        }
    }
}
