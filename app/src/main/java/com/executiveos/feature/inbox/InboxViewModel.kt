package com.executiveos.feature.inbox

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.InboxState
import com.executiveos.domain.repository.InboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InboxTab { PENDING, POSTPONED, PROCESSED }

enum class PostponeOption(val days: Long) { TOMORROW(1), IN_THREE_DAYS(3), NEXT_WEEK(7) }

data class InboxUiState(
    val isLoading: Boolean = true,
    val tab: InboxTab = InboxTab.PENDING,
    val pending: List<InboxEntry> = emptyList(),
    val postponed: List<InboxEntry> = emptyList(),
    val processed: List<InboxEntry> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) {
    val visible: List<InboxEntry>
        get() = when (tab) {
            InboxTab.PENDING -> pending
            InboxTab.POSTPONED -> postponed
            InboxTab.PROCESSED -> processed
        }
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: InboxRepository,
    private val clock: Clock,
) : ViewModel() {
    private val tab = MutableStateFlow(InboxTab.PENDING)
    private val selectedIds = MutableStateFlow(emptySet<String>())
    private val messageChannel = Channel<Int>(Channel.BUFFERED)

    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<InboxUiState> = combine(
        repository.observeEntries(),
        tab,
        selectedIds,
    ) { entries, currentTab, selected ->
        val today = LocalDate.now(clock)
        InboxUiState(
            isLoading = false,
            tab = currentTab,
            pending = entries.filter { it.isPendingOn(today) },
            postponed = entries.filter { it.isPostponedPast(today) },
            processed = entries.filter { it.isClosed() },
            selectedIds = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = InboxUiState(),
    )

    fun onTabSelected(selected: InboxTab) {
        selectedIds.value = emptySet()
        tab.value = selected
    }

    fun onToggleSelection(itemId: String) {
        selectedIds.update { current ->
            if (itemId in current) current - itemId else current + itemId
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun convertToTasks(itemIds: Collection<String>) =
        perform(R.string.inbox_done_to_task) { repository.convertToTasks(itemIds) }

    fun convertToProject(itemId: String) =
        perform(R.string.inbox_done_to_project) { repository.convertToProject(itemId) }

    fun convertToNote(itemId: String) =
        perform(R.string.inbox_done_to_note) { repository.convertToNote(itemId) }

    fun delegate(itemId: String, person: String) =
        perform(R.string.inbox_done_delegated) { repository.delegate(itemId, person) }

    fun postpone(itemIds: Collection<String>, option: PostponeOption) =
        perform(R.string.inbox_done_postponed) {
            repository.postpone(itemIds, LocalDate.now(clock).plusDays(option.days))
        }

    fun archive(itemIds: Collection<String>) =
        perform(R.string.inbox_done_archived) { repository.archive(itemIds) }

    fun delete(itemIds: Collection<String>) =
        perform(R.string.inbox_done_deleted) { repository.delete(itemIds) }

    private fun perform(@StringRes success: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                action()
                selectedIds.value = emptySet()
                success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                R.string.inbox_action_failed
            }
            messageChannel.send(message)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun InboxEntry.isPostponedPast(today: LocalDate): Boolean =
    item.state == InboxState.POSTPONED && item.postponedUntil?.isAfter(today) == true

/** Una captura pospuesta vuelve sola a Pendientes al llegar su fecha. */
private fun InboxEntry.isPendingOn(today: LocalDate): Boolean = item.state == InboxState.PENDING ||
    (item.state == InboxState.POSTPONED && !isPostponedPast(today))

private fun InboxEntry.isClosed(): Boolean =
    item.state == InboxState.PROCESSED || item.state == InboxState.ARCHIVED
