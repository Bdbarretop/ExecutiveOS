package com.executiveos.feature.planning

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.formatDuration
import com.executiveos.core.designsystem.component.toDayTitle
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.component.toMonthTitle
import com.executiveos.core.designsystem.component.toShortDayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.CapacitySummary
import com.executiveos.domain.planning.PlanningPeriod
import com.executiveos.domain.planning.TimeBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

const val PLANNING_ADD_TAG = "planning_add"

private const val MOVE_STEP_MINUTES = 30L
private const val DAYS_PER_WEEK = 7
private const val MINUTES_PER_HOUR = 60.0
private const val MAX_MINUTES_PER_DAY = 1_440
private const val MAX_PERCENT = 100
private val SPANISH: Locale = Locale.forLanguageTag("es")

@StringRes
internal fun BlockType.labelRes(): Int = when (this) {
    BlockType.DEEP_WORK -> R.string.block_type_deep
    BlockType.COORDINATION -> R.string.block_type_coordination
    BlockType.STUDY -> R.string.block_type_study
    BlockType.PERSONAL -> R.string.block_type_personal
}

@StringRes
private fun PlanningPeriod.labelRes(): Int = when (this) {
    PlanningPeriod.DAY -> R.string.planning_view_day
    PlanningPeriod.WEEK -> R.string.planning_view_week
    PlanningPeriod.MONTH -> R.string.planning_view_month
}

private fun TimeBlock.timeRange(zone: ZoneId): String {
    val start = startAt.atZone(zone).toLocalTime().toDisplayText()
    val end = endAt.atZone(zone).toLocalTime().toDisplayText()
    return "$start–$end"
}

@Composable
fun PlanningScreen(
    onNavigateUp: () -> Unit,
    onOpenBlock: (String) -> Unit,
    onNewBlock: (LocalDate) -> Unit,
    viewModel: PlanningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showCapacity by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(
                title = stringResource(R.string.section_planning),
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { showCapacity = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tune),
                            contentDescription = stringResource(R.string.planning_adjust),
                        )
                    }
                },
            )
            val state = uiState
            if (state == null) {
                LoadingState()
            } else {
                PlanningContent(state, viewModel, onOpenBlock, onNewBlock)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val settings = uiState?.settings
    if (showCapacity && settings != null) {
        CapacityDialog(
            settings = settings,
            onDismiss = { showCapacity = false },
            onSave = {
                showCapacity = false
                viewModel.saveCapacity(it)
            },
        )
    }
}

@Composable
private fun PlanningContent(
    state: PlanningUiState,
    viewModel: PlanningViewModel,
    onOpenBlock: (String) -> Unit,
    onNewBlock: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PeriodSelector(state.period, viewModel::selectPeriod)
        PeriodHeader(state, viewModel)
        CapacityCard(state.capacity, state.settings.targetPercent)
        FilledTonalButton(
            onClick = { onNewBlock(state.anchor) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PLANNING_ADD_TAG),
        ) {
            Text(stringResource(R.string.planning_add_block))
        }
        when (state.period) {
            PlanningPeriod.DAY -> DayContent(state, viewModel, onOpenBlock)
            PlanningPeriod.WEEK -> WeekContent(state, viewModel::openDay)
            PlanningPeriod.MONTH -> MonthContent(state, viewModel::openDay)
        }
        ExecutionSection(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: PlanningPeriod, onSelect: (PlanningPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PlanningPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, PlanningPeriod.entries.size),
                label = { Text(stringResource(period.labelRes())) },
            )
        }
    }
}

@Composable
private fun PeriodHeader(state: PlanningUiState, viewModel: PlanningViewModel) {
    val showsToday = !state.today.isBefore(state.span.from) && !state.today.isAfter(state.span.to)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::previous) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.planning_previous),
                modifier = Modifier.rotate(180f),
            )
        }
        Text(
            text = periodTitle(state),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::next) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.planning_next),
            )
        }
        if (!showsToday) {
            TextButton(onClick = viewModel::goToToday) {
                Text(stringResource(R.string.planning_today))
            }
        }
    }
}

@Composable
private fun periodTitle(state: PlanningUiState): String = when (state.period) {
    PlanningPeriod.DAY -> state.anchor.toDayTitle()
    PlanningPeriod.WEEK -> stringResource(
        R.string.planning_week_range,
        state.span.from.toShortDayText(),
        state.span.to.toShortDayText(),
    )
    PlanningPeriod.MONTH -> state.anchor.toMonthTitle()
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CapacityCard(capacity: CapacitySummary, targetPercent: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SectionTitle(R.string.planning_capacity_title)
            ValueRow(stringResource(R.string.planning_available), formatDuration(capacity.availableMinutes))
            ValueRow(
                stringResource(R.string.planning_target, targetPercent),
                formatDuration(capacity.targetMinutes),
            )
            ValueRow(stringResource(R.string.planning_planned), formatDuration(capacity.plannedMinutes))
            ValueRow(stringResource(R.string.planning_remaining), formatDuration(capacity.remainingMinutes))
            ValueRow(stringResource(R.string.planning_actual), formatDuration(capacity.actualMinutes))
            if (capacity.isOverloaded) {
                Text(
                    text = stringResource(
                        R.string.planning_overloaded,
                        formatDuration(capacity.plannedMinutes - capacity.targetMinutes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (capacity.availableMinutes == 0) {
                Text(
                    text = stringResource(R.string.planning_no_capacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayContent(
    state: PlanningUiState,
    viewModel: PlanningViewModel,
    onOpenBlock: (String) -> Unit,
) {
    val blocks = state.days.single().blocks
    var loggingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (blocks.isEmpty()) {
            Text(
                text = stringResource(R.string.planning_day_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        blocks.forEach { block ->
            BlockCard(
                block = block,
                state = state,
                onOpen = { onOpenBlock(block.id) },
                onMove = { days, minutes -> viewModel.move(block, days, minutes) },
                onLogActual = { loggingId = block.id },
                onDelete = { deletingId = block.id },
            )
        }
    }
    blocks.firstOrNull { it.id == loggingId }?.let { block ->
        ActualTimeDialog(
            block = block,
            onDismiss = { loggingId = null },
            onSave = {
                loggingId = null
                viewModel.logActual(block, it)
            },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.block_delete_title)) },
            text = { Text(stringResource(R.string.block_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingId = null
                        viewModel.delete(id)
                    },
                ) {
                    Text(stringResource(R.string.inbox_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun BlockCard(
    block: TimeBlock,
    state: PlanningUiState,
    onOpen: () -> Unit,
    onMove: (days: Long, minutes: Long) -> Unit,
    onLogActual: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = block.timeRange(state.zone),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(block.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(block.type.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                block.taskId?.let(state.taskTitles::get)?.let { title ->
                    Text(
                        text = stringResource(R.string.block_task, title),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (block.id in state.overlappingIds) {
                    Text(
                        text = stringResource(R.string.block_overlaps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = block.actualMinutes?.let { actual ->
                        stringResource(
                            R.string.block_plan_actual,
                            formatDuration(block.plannedMinutes),
                            formatDuration(actual),
                        )
                    } ?: stringResource(R.string.block_plan_only, formatDuration(block.plannedMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BlockMenu(onMove, onLogActual, onDelete)
        }
    }
}

@Composable
private fun BlockMenu(
    onMove: (days: Long, minutes: Long) -> Unit,
    onLogActual: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val select: (() -> Unit) -> Unit = { action ->
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.action_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_move_earlier)) },
                onClick = { select { onMove(0, -MOVE_STEP_MINUTES) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_move_later)) },
                onClick = { select { onMove(0, MOVE_STEP_MINUTES) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_move_next_day)) },
                onClick = { select { onMove(1, 0) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_log_actual)) },
                onClick = { select(onLogActual) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_delete)) },
                onClick = { select(onDelete) },
            )
        }
    }
}

@Composable
private fun WeekContent(state: PlanningUiState, onOpenDay: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.days.forEach { day ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                border = dayBorder(day.date == state.today),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDay(day.date) },
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ValueRow(day.date.toDayTitle(), formatDuration(day.plannedMinutes))
                    if (day.isOverloaded) {
                        WarningLabel(R.string.planning_overloaded_short)
                    }
                    if (day.blocks.any { it.id in state.overlappingIds }) {
                        WarningLabel(R.string.planning_overlaps_short)
                    }
                    day.blocks.forEach { block ->
                        Text(
                            text = "${block.timeRange(state.zone)}  ${block.title}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningLabel(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun dayBorder(isToday: Boolean): BorderStroke? =
    if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

@Composable
private fun MonthContent(state: PlanningUiState, onOpenDay: (LocalDate) -> Unit) {
    val leadingBlanks = state.days.first().date.dayOfWeek.value - 1
    val cells: List<DayPlan?> = List(leadingBlanks) { null } + state.days
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, SPANISH),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        MonthCell(
                            day = day,
                            isToday = day.date == state.today,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenDay(day.date) },
                        )
                    }
                }
                repeat(DAYS_PER_WEEK - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthCell(day: DayPlan, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = when {
            day.isOverloaded -> colors.tertiaryContainer
            day.blocks.isNotEmpty() -> colors.surfaceContainerHigh
            else -> colors.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.small,
        border = dayBorder(isToday),
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
            if (day.plannedMinutes > 0) {
                Text(
                    text = String.format(SPANISH, "%.1f h", day.plannedMinutes / MINUTES_PER_HOUR),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExecutionSection(state: PlanningUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(R.string.planning_execution_title)
        if (state.execution.isEmpty()) {
            Text(
                text = stringResource(R.string.planning_execution_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.execution.forEach { item ->
            Column {
                Text(stringResource(item.type.labelRes()), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = if (item.comparablePlannedMinutes == 0) {
                        stringResource(
                            R.string.planning_execution_no_actual,
                            formatDuration(item.plannedMinutes),
                        )
                    } else {
                        stringResource(
                            R.string.planning_execution_compared,
                            formatDuration(item.plannedMinutes),
                            formatDuration(item.comparablePlannedMinutes),
                            formatDuration(item.actualMinutes),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActualTimeDialog(block: TimeBlock, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(block.actualMinutes?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_log_actual)) },
        text = {
            OutlinedTextField(
                value = minutes,
                onValueChange = { text -> if (text.all(Char::isDigit)) minutes = text },
                label = { Text(stringResource(R.string.block_field_actual)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(minutes.toIntOrNull()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapacityDialog(
    settings: CapacitySettings,
    onDismiss: () -> Unit,
    onSave: (CapacitySettings) -> Unit,
) {
    var minutes by rememberSaveable { mutableStateOf(settings.availableMinutesPerDay.toString()) }
    var percent by rememberSaveable { mutableStateOf(settings.targetPercent.toString()) }
    var days by rememberSaveable { mutableStateOf(settings.workDays) }
    val parsedMinutes = minutes.toIntOrNull()?.takeIf { it in 0..MAX_MINUTES_PER_DAY }
    val parsedPercent = percent.toIntOrNull()?.takeIf { it in 1..MAX_PERCENT }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planning_adjust)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(stringResource(R.string.capacity_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { text -> if (text.all(Char::isDigit)) minutes = text },
                    label = { Text(stringResource(R.string.capacity_minutes)) },
                    isError = parsedMinutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = percent,
                    onValueChange = { text -> if (text.all(Char::isDigit)) percent = text },
                    label = { Text(stringResource(R.string.capacity_percent)) },
                    isError = parsedPercent == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.capacity_days), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, SPANISH)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedMinutes != null && parsedPercent != null,
                onClick = {
                    if (parsedMinutes != null && parsedPercent != null) {
                        onSave(CapacitySettings(parsedMinutes, parsedPercent, days))
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
