package com.executiveos.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTimePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.core.notifications.rememberNotificationPermissionRequest
import com.executiveos.domain.reminders.ReminderScheduler
import com.executiveos.domain.reminders.ReminderSettings
import com.executiveos.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val SPANISH: Locale = Locale.forLanguageTag("es")
private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(SPANISH)
private val DAYS_BEFORE_OPTIONS = listOf(0, 1, 3, 7)

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val settings: StateFlow<ReminderSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun update(transform: ReminderSettings.() -> ReminderSettings) {
        val updated = settings.value?.transform() ?: return
        viewModelScope.launch {
            repository.saveSettings(updated)
            scheduler.scheduleRecurring(updated, replaceExisting = true)
        }
    }
}

@Composable
fun ReminderSettingsScreen(
    onClose: () -> Unit,
    viewModel: ReminderSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        ExecutiveTopBar(title = stringResource(R.string.settings_reminders), onNavigateUp = onClose)
        val current = settings
        if (current == null) {
            LoadingState()
        } else {
            ReminderForm(current, viewModel::update)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderForm(
    settings: ReminderSettings,
    update: (ReminderSettings.() -> ReminderSettings) -> Unit,
) {
    val requestPermission = rememberNotificationPermissionRequest()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PermissionNotice(requestPermission)
        GroupTitle(R.string.reminders_commitments)
        SwitchRow(R.string.reminders_deadlines, settings.deadlineRemindersEnabled) { enabled ->
            if (enabled) requestPermission()
            update { copy(deadlineRemindersEnabled = enabled) }
        }
        if (settings.deadlineRemindersEnabled) {
            Text(stringResource(R.string.reminders_days_before), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DAYS_BEFORE_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = settings.deadlineDaysBefore == days,
                        onClick = { update { copy(deadlineDaysBefore = days) } },
                        label = { Text(days.toString()) },
                    )
                }
            }
        }
        SwitchRow(R.string.reminders_critical, settings.criticalAlertsEnabled) { enabled ->
            if (enabled) requestPermission()
            update { copy(criticalAlertsEnabled = enabled) }
        }
        TimeRow(R.string.reminders_morning_time, settings.morningNoticeMinute) { minute ->
            update { copy(morningNoticeMinute = minute) }
        }
        GroupTitle(R.string.reminders_reviews)
        SwitchRow(R.string.reminders_daily_review, settings.dailyReviewEnabled) { enabled ->
            if (enabled) requestPermission()
            update { copy(dailyReviewEnabled = enabled) }
        }
        if (settings.dailyReviewEnabled) {
            TimeRow(R.string.reminders_time, settings.dailyReviewMinute) { minute ->
                update { copy(dailyReviewMinute = minute) }
            }
        }
        SwitchRow(R.string.reminders_weekly_review, settings.weeklyReviewEnabled) { enabled ->
            if (enabled) requestPermission()
            update { copy(weeklyReviewEnabled = enabled) }
        }
        if (settings.weeklyReviewEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = settings.weeklyReviewDay == day,
                        onClick = { update { copy(weeklyReviewDay = day) } },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, SPANISH)) },
                    )
                }
            }
            TimeRow(R.string.reminders_time, settings.weeklyReviewMinute) { minute ->
                update { copy(weeklyReviewMinute = minute) }
            }
        }
        GroupTitle(R.string.reminders_quiet)
        SwitchRow(R.string.reminders_quiet_enable, settings.quietHoursEnabled) { enabled ->
            update { copy(quietHoursEnabled = enabled) }
        }
        if (settings.quietHoursEnabled) {
            TimeRow(R.string.reminders_quiet_start, settings.quietStartMinute) { minute ->
                update { copy(quietStartMinute = minute) }
            }
            TimeRow(R.string.reminders_quiet_end, settings.quietEndMinute) { minute ->
                update { copy(quietEndMinute = minute) }
            }
        }
        Text(
            text = stringResource(R.string.reminders_approximate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionNotice(requestPermission: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { NotificationManagerCompat.from(context) }
    var enabled by remember { mutableStateOf(manager.areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = manager.areNotificationsEnabled() }
    if (enabled) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = requestPermission),
    ) {
        Text(
            text = stringResource(R.string.reminders_permission_needed),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

@Composable
private fun GroupTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeRow(@StringRes label: Int, minuteOfDay: Int, onChange: (Int) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label))
        Text(time.format(TIME_FORMAT), color = MaterialTheme.colorScheme.primary)
    }
    if (showPicker) {
        ExecutiveTimePickerDialog(
            initialTime = time,
            onDismiss = { showPicker = false },
            onTimeSelected = {
                onChange(it.hour * 60 + it.minute)
                showPicker = false
            },
        )
    }
}
