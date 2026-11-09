package com.executiveos.feature.reviews

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.formatDuration
import com.executiveos.core.designsystem.component.toDayTitle
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.reviews.Review
import com.executiveos.domain.reviews.ReviewQuestion
import com.executiveos.domain.reviews.ReviewType
import com.executiveos.domain.reviews.WeeklyRecommendation
import com.executiveos.domain.reviews.WeeklySummary
import com.executiveos.feature.tasks.SaveNotice
import java.time.LocalDate

fun reviewOpenTag(type: ReviewType): String = "review_open_${type.name.lowercase()}"

fun reviewAnswerTag(question: ReviewQuestion): String = "review_answer_${question.name.lowercase()}"

@StringRes
private fun ReviewType.labelRes(): Int = when (this) {
    ReviewType.DAILY -> R.string.review_daily
    ReviewType.CLOSING -> R.string.review_closing
    ReviewType.WEEKLY -> R.string.review_weekly
}

@StringRes
private fun ReviewQuestion.textRes(): Int = when (this) {
    ReviewQuestion.TASKS_TODAY -> R.string.review_q_tasks_today
    ReviewQuestion.TOP_PRIORITIES -> R.string.review_q_priorities
    ReviewQuestion.DUE_COMMITMENTS -> R.string.review_q_due
    ReviewQuestion.BLOCKED_ITEMS -> R.string.review_q_blocked
    ReviewQuestion.CAN_DELEGATE -> R.string.review_q_delegate
    ReviewQuestion.CONSCIOUSLY_POSTPONE -> R.string.review_q_postpone
    ReviewQuestion.PROTECT_TIME -> R.string.review_q_protect
    ReviewQuestion.FINISHED -> R.string.review_q_finished
    ReviewQuestion.STILL_PENDING -> R.string.review_q_pending
    ReviewQuestion.WHY_PENDING -> R.string.review_q_why
    ReviewQuestion.WHAT_CHANGED -> R.string.review_q_changed
    ReviewQuestion.PREPARE_TOMORROW -> R.string.review_q_tomorrow
    ReviewQuestion.WEEKLY_NOTES -> R.string.review_q_notes
}

@StringRes
private fun WeeklyRecommendation.textRes(): Int = when (this) {
    WeeklyRecommendation.CLEAR_OVERDUE -> R.string.recommend_overdue
    WeeklyRecommendation.REVIEW_DELAYED_PROJECTS -> R.string.recommend_delayed
    WeeklyRecommendation.REDUCE_OVERLOAD -> R.string.recommend_overload
    WeeklyRecommendation.PROTECT_DEEP_WORK -> R.string.recommend_deep_work
    WeeklyRecommendation.SCHEDULE_LEARNING -> R.string.recommend_learning
    WeeklyRecommendation.PREPARE_COMMITMENTS -> R.string.recommend_commitments
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
private fun periodText(type: ReviewType, date: LocalDate): String =
    if (type == ReviewType.WEEKLY) {
        stringResource(R.string.review_week_of, date.toDisplayText())
    } else {
        date.toDayTitle()
    }

@Composable
fun ReviewsScreen(
    onNavigateUp: () -> Unit,
    onOpenReview: (ReviewType, LocalDate?) -> Unit,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        ExecutiveTopBar(title = stringResource(R.string.section_reviews), onNavigateUp = onNavigateUp)
        val state = uiState
        if (state == null) {
            LoadingState()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = Spacing.fabClearance),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SectionTitle(R.string.review_current)
                ReviewType.entries.forEach { type ->
                    CurrentReviewCard(
                        type = type,
                        isPending = type in state.pendingTypes,
                        onClick = { onOpenReview(type, null) },
                    )
                }
                SectionTitle(R.string.review_history)
                if (state.history.isEmpty()) {
                    Text(
                        text = stringResource(R.string.review_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.history.forEach { review ->
                    HistoryRow(review, onClick = { onOpenReview(review.type, review.periodDate) })
                }
            }
        }
    }
}

@Composable
private fun CurrentReviewCard(type: ReviewType, isPending: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(reviewOpenTag(type)),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(type.labelRes()), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(if (isPending) R.string.review_pending else R.string.review_done),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPending) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun HistoryRow(review: Review, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(review.type.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = review.periodDate.toDisplayText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ReviewEditorScreen(
    onClose: () -> Unit,
    viewModel: ReviewEditorViewModel = hiltViewModel(),
) {
    val answers by viewModel.answers.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val close = { viewModel.flush(onClose) }
    BackHandler(onBack = close)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ExecutiveTopBar(title = stringResource(viewModel.type.labelRes()), onNavigateUp = close)
        val current = answers
        if (current == null) {
            LoadingState()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = periodText(viewModel.type, viewModel.periodDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SaveNotice(saveState)
                summary?.let { WeeklySummaryContent(it) }
                viewModel.questions.forEach { question ->
                    OutlinedTextField(
                        value = current[question].orEmpty(),
                        onValueChange = { viewModel.onAnswerChange(question, it) },
                        label = { Text(stringResource(question.textRes())) },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(reviewAnswerTag(question)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklySummaryContent(summary: WeeklySummary) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SectionTitle(R.string.review_summary_title)
            SummaryRow(R.string.review_summary_completed, summary.completedTasks.toString())
            SummaryRow(R.string.review_summary_pending, summary.pendingTasks.toString())
            SummaryRow(R.string.review_summary_overdue, summary.overdueTasks.toString())
            SummaryRow(R.string.review_summary_projects, summary.activeProjects.toString())
            SummaryRow(R.string.review_summary_delayed, summary.delayedProjects.toString())
            SummaryRow(R.string.review_summary_learning, formatDuration(summary.learningMinutes))
            SummaryRow(R.string.review_summary_deep_work, formatDuration(summary.deepWorkMinutes))
            SummaryRow(R.string.review_summary_overload, summary.overloadedDays.toString())
            SummaryRow(R.string.review_summary_next, summary.nextWeekCommitments.toString())
            Text(
                text = stringResource(R.string.review_summary_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(R.string.review_recommendations)
        Text(
            text = stringResource(R.string.review_recommendations_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.recommendations.isEmpty()) {
            Text(
                text = stringResource(R.string.review_recommendations_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        summary.recommendations.forEach { recommendation ->
            Text(
                text = stringResource(recommendation.textRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SummaryRow(@StringRes label: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
