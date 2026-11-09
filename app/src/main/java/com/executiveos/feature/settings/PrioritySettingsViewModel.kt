package com.executiveos.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Texto editable: permite escribir «4,00» sin reformatear a cada pulsación. */
data class PrioritySettingsForm(
    val impact: String,
    val urgency: String,
    val delay: String,
    val critical: String,
    val high: String,
    val medium: String,
) {
    fun toSettings(): PrioritySettings? {
        return PrioritySettings(
            impactWeight = impact.toIntOrNull() ?: return null,
            urgencyWeight = urgency.toIntOrNull() ?: return null,
            delayRiskWeight = delay.toIntOrNull() ?: return null,
            criticalThreshold = critical.toHundredths() ?: return null,
            highThreshold = high.toHundredths() ?: return null,
            mediumThreshold = medium.toHundredths() ?: return null,
        ).takeIf { it.isValid }
    }
}

fun PrioritySettings.toForm() = PrioritySettingsForm(
    impact = impactWeight.toString(),
    urgency = urgencyWeight.toString(),
    delay = delayRiskWeight.toString(),
    critical = criticalThreshold.toDecimalText(),
    high = highThreshold.toDecimalText(),
    medium = mediumThreshold.toDecimalText(),
)

private fun String.toHundredths(): Int? =
    replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.toInt()

private fun Int.toDecimalText(): String =
    String.format(Locale.forLanguageTag("es"), "%.2f", this / 100.0)

@HiltViewModel
class PrioritySettingsViewModel @Inject constructor(
    private val settingsRepository: PrioritySettingsRepository,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _form = MutableStateFlow<PrioritySettingsForm?>(null)
    val form: StateFlow<PrioritySettingsForm?> = _form

    private val _pendingConfirmation = MutableStateFlow<Int?>(null)

    /** Número de tareas que cambiarían de nivel, mientras se espera la confirmación. */
    val pendingConfirmation: StateFlow<Int?> = _pendingConfirmation

    private val closeChannel = Channel<Unit>(Channel.CONFLATED)
    val closeEvents: Flow<Unit> = closeChannel.receiveAsFlow()

    init {
        viewModelScope.launch { _form.value = settingsRepository.settings.first().toForm() }
    }

    fun onFormChange(transform: PrioritySettingsForm.() -> PrioritySettingsForm) {
        _form.value = _form.value?.transform()
    }

    fun resetToDefaults() {
        _form.value = PrioritySettings().toForm()
    }

    /** Las prioridades no cambian en silencio: antes se informa de cuántas tareas afecta. */
    fun requestSave() {
        val updated = _form.value?.toSettings() ?: return
        viewModelScope.launch {
            val changes = levelChanges(settingsRepository.settings.first(), updated)
            if (changes == 0) persist(updated) else _pendingConfirmation.value = changes
        }
    }

    fun confirmSave() {
        val updated = _form.value?.toSettings() ?: return
        viewModelScope.launch { persist(updated) }
    }

    fun dismissConfirmation() {
        _pendingConfirmation.value = null
    }

    private suspend fun persist(settings: PrioritySettings) {
        _pendingConfirmation.value = null
        settingsRepository.save(settings)
        closeChannel.send(Unit)
    }

    private suspend fun levelChanges(current: PrioritySettings, updated: PrioritySettings): Int {
        val today = LocalDate.now(clock)
        val open = taskRepository.observeTaskList().first()
            .filter { TaskQueries.isOpen(it.task.status) && it.task.manualPriority == null }
        val before = TaskQueries.rank(open, current, today)
        val after = TaskQueries.rank(open, updated, today)
        return before.zip(after).count { (old, new) ->
            old.priority.calculatedLevel != new.priority.calculatedLevel
        }
    }
}
