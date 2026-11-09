package com.executiveos.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.executiveos.R
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.TaskHistoryAction
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.CriticalObligation
import com.executiveos.domain.priority.PriorityAssessment
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.priority.PriorityReason
import com.executiveos.domain.priority.PriorityScale
import com.executiveos.domain.tasks.EstimateFilter
import com.executiveos.domain.tasks.TaskView
import java.util.Locale

@StringRes
fun TaskView.labelRes(): Int = when (this) {
    TaskView.ALL -> R.string.view_all
    TaskView.TODAY -> R.string.view_today
    TaskView.UPCOMING -> R.string.view_upcoming
    TaskView.OVERDUE -> R.string.view_overdue
    TaskView.CRITICAL -> R.string.view_critical
    TaskView.HIGH -> R.string.view_high
    TaskView.IN_PROGRESS -> R.string.view_in_progress
    TaskView.WAITING -> R.string.view_waiting
    TaskView.BLOCKED -> R.string.view_blocked
    TaskView.COMPLETED -> R.string.view_completed
    TaskView.NO_DATE -> R.string.view_no_date
}

@StringRes
fun TaskStatus.labelRes(): Int = when (this) {
    TaskStatus.INBOX -> R.string.status_inbox
    TaskStatus.PENDING -> R.string.status_pending
    TaskStatus.IN_PROGRESS -> R.string.status_in_progress
    TaskStatus.WAITING -> R.string.status_waiting
    TaskStatus.BLOCKED -> R.string.status_blocked
    TaskStatus.COMPLETED -> R.string.status_completed
    TaskStatus.CANCELLED -> R.string.status_cancelled
    TaskStatus.ARCHIVED -> R.string.status_archived
}

@StringRes
fun PriorityLevel.labelRes(): Int = when (this) {
    PriorityLevel.CRITICAL -> R.string.level_critical
    PriorityLevel.HIGH -> R.string.level_high
    PriorityLevel.MEDIUM -> R.string.level_medium
    PriorityLevel.LOW -> R.string.level_low
}

@StringRes
private fun PriorityLevel.wordRes(): Int = when (this) {
    PriorityLevel.CRITICAL -> R.string.level_critical_word
    PriorityLevel.HIGH -> R.string.level_high_word
    PriorityLevel.MEDIUM -> R.string.level_medium_word
    PriorityLevel.LOW -> R.string.level_low_word
}

@StringRes
fun CriticalObligation.labelRes(): Int = when (this) {
    CriticalObligation.REGULATORY -> R.string.obligation_regulatory
    CriticalObligation.LEGAL -> R.string.obligation_legal
    CriticalObligation.AUDIT -> R.string.obligation_audit
    CriticalObligation.MATERIAL_RISK -> R.string.obligation_material_risk
    CriticalObligation.CONTRACTUAL -> R.string.obligation_contractual
    CriticalObligation.EXECUTIVE_COMMITMENT -> R.string.obligation_executive
}

@StringRes
fun EstimateFilter.labelRes(): Int = when (this) {
    EstimateFilter.UP_TO_30 -> R.string.estimate_up_to_30
    EstimateFilter.UP_TO_60 -> R.string.estimate_up_to_60
    EstimateFilter.UP_TO_120 -> R.string.estimate_up_to_120
    EstimateFilter.OVER_120 -> R.string.estimate_over_120
}

@StringRes
fun PriorityScale.labelRes(): Int = when (this) {
    PriorityScale.IMPACT -> R.string.scale_impact
    PriorityScale.URGENCY -> R.string.scale_urgency
    PriorityScale.DELAY_RISK -> R.string.scale_delay
}

@StringRes
private fun PriorityReason.labelRes(): Int = when (this) {
    PriorityReason.HIGH_IMPACT -> R.string.reason_high_impact
    PriorityReason.HIGH_URGENCY -> R.string.reason_high_urgency
    PriorityReason.SERIOUS_DELAY_RISK -> R.string.reason_delay_risk
    PriorityReason.OVERDUE -> R.string.reason_overdue
    PriorityReason.DUE_SOON -> R.string.reason_due_soon
    PriorityReason.PENDING_DEPENDENCIES -> R.string.reason_dependencies
    PriorityReason.CRITICAL_OBLIGATION -> R.string.reason_obligation
}

@StringRes
fun TaskHistoryAction.labelRes(): Int = when (this) {
    TaskHistoryAction.CAPTURED -> R.string.history_captured
    TaskHistoryAction.CREATED -> R.string.history_created
    TaskHistoryAction.DUPLICATED -> R.string.history_duplicated
    TaskHistoryAction.CONVERTED_TO_TASK -> R.string.history_to_task
    TaskHistoryAction.CONVERTED_TO_PROJECT -> R.string.history_to_project
    TaskHistoryAction.CONVERTED_TO_NOTE -> R.string.history_to_note
    TaskHistoryAction.DELEGATED -> R.string.history_delegated
    TaskHistoryAction.POSTPONED -> R.string.history_postponed
    TaskHistoryAction.ARCHIVED -> R.string.history_archived
    TaskHistoryAction.STATUS_CHANGED -> R.string.history_status
    TaskHistoryAction.PRIORITY_CHANGED -> R.string.history_priority
    TaskHistoryAction.MANUAL_PRIORITY_CHANGED -> R.string.history_manual
    TaskHistoryAction.OBLIGATION_CHANGED -> R.string.history_obligation
    TaskHistoryAction.DATE_CHANGED -> R.string.history_date
    TaskHistoryAction.SUBTASK_ADDED -> R.string.history_subtask
    TaskHistoryAction.DEPENDENCY_ADDED -> R.string.history_dependency_added
    TaskHistoryAction.DEPENDENCY_REMOVED -> R.string.history_dependency_removed
}

/** Centésimas a texto con coma decimal, por ejemplo 415 a «4,15». */
fun formatHundredths(value: Int): String = String.format(Locale.forLanguageTag("es"), "%.2f", value / 100.0)

@Composable
fun PriorityBadge(level: PriorityLevel?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (level) {
        PriorityLevel.CRITICAL -> colors.error to colors.onError
        PriorityLevel.HIGH -> colors.tertiaryContainer to colors.onTertiaryContainer
        PriorityLevel.MEDIUM -> colors.primaryContainer to colors.onPrimaryContainer
        PriorityLevel.LOW -> colors.secondaryContainer to colors.onSecondaryContainer
        null -> Color.Transparent to colors.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(level?.labelRes() ?: R.string.level_pending),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
fun ObligationBadge(obligation: CriticalObligation, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(obligation.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

/** Estado del autoguardado de una ficha; en reposo no ocupa sitio. */
@Composable
fun SaveNotice(state: SaveState) {
    val text = when (state) {
        SaveState.IDLE -> return
        SaveState.SAVED -> R.string.task_saved
        SaveState.INVALID -> R.string.task_invalid
        SaveState.FAILED -> R.string.task_save_failed
    }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = if (state == SaveState.SAVED) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

/** Frase comprensible: «Prioridad alta porque tiene impacto elevado y una fecha próxima». */
@Composable
fun priorityExplanation(assessment: PriorityAssessment): String {
    val level = assessment.effectiveLevel
    if (level == null) {
        val missing = assessment.missingScales.map { stringResource(it.labelRes()) }
        return stringResource(R.string.priority_pending, joinWithAnd(missing))
    }
    val word = stringResource(level.wordRes())
    val reasons = assessment.reasons.map { stringResource(it.labelRes()) }
    return if (reasons.isEmpty()) {
        stringResource(R.string.priority_from_scales, word)
    } else {
        stringResource(R.string.priority_because, word, joinWithAnd(reasons))
    }
}

@Composable
private fun joinWithAnd(items: List<String>): String {
    if (items.size < 2) return items.joinToString()
    val and = stringResource(R.string.list_join_and)
    return items.dropLast(1).joinToString(", ") + " $and " + items.last()
}
