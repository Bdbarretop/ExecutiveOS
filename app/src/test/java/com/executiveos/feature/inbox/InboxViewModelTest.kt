package com.executiveos.feature.inbox

import com.executiveos.R
import com.executiveos.domain.model.DateOption
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.InboxItem
import com.executiveos.domain.model.InboxState
import com.executiveos.domain.repository.InboxRepository
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val repository = mockk<InboxRepository>(relaxUnitFun = true)

    private fun entry(id: String, state: InboxState, postponedUntil: LocalDate? = null) = InboxEntry(
        item = InboxItem(
            id = id,
            originalTitle = id,
            originalDetails = null,
            categoryId = null,
            dateOption = DateOption.NONE,
            capturedDate = null,
            dateIsDeadline = false,
            state = state,
            postponedUntil = postponedUntil,
            processedAs = null,
            processedTargetId = null,
            createdAt = Instant.EPOCH,
        ),
        taskId = "t-$id",
        title = id,
        categoryName = null,
    )

    private fun viewModelWith(vararg entries: InboxEntry): InboxViewModel {
        every { repository.observeEntries() } returns flowOf(entries.toList())
        return InboxViewModel(repository, clock)
    }

    private fun TestScope.collectState(viewModel: InboxViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
    }

    @Test
    fun entriesAreGroupedByProcessingState() = runTest {
        val viewModel = viewModelWith(
            entry("pendiente", InboxState.PENDING),
            entry("pospuesta", InboxState.POSTPONED, today.plusDays(2)),
            entry("procesada", InboxState.PROCESSED),
            entry("archivada", InboxState.ARCHIVED),
        )

        collectState(viewModel)

        val state = viewModel.uiState.value
        assertEquals(listOf("pendiente"), state.pending.map { it.item.id })
        assertEquals(listOf("pospuesta"), state.postponed.map { it.item.id })
        assertEquals(listOf("procesada", "archivada"), state.processed.map { it.item.id })
    }

    @Test
    fun postponedCaptureReturnsToPendingWhenItsDateArrives() = runTest {
        val viewModel = viewModelWith(entry("vencida", InboxState.POSTPONED, today))

        collectState(viewModel)

        assertEquals(listOf("vencida"), viewModel.uiState.value.pending.map { it.item.id })
        assertTrue(viewModel.uiState.value.postponed.isEmpty())
    }

    @Test
    fun postponeOptionsAreCountedFromToday() = runTest {
        val viewModel = viewModelWith()

        viewModel.postpone(listOf("a"), PostponeOption.NEXT_WEEK)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.postpone(listOf("a"), today.plusDays(7)) }
    }

    @Test
    fun selectionTogglesAndClearsAfterABatchAction() = runTest {
        val viewModel = viewModelWith(entry("a", InboxState.PENDING))
        collectState(viewModel)

        viewModel.onToggleSelection("a")
        advanceUntilIdle()
        assertEquals(setOf("a"), viewModel.uiState.value.selectedIds)

        viewModel.convertToTasks(viewModel.uiState.value.selectedIds)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertEquals(R.string.inbox_done_to_task, viewModel.messages.first())
    }

    @Test
    fun failedActionKeepsTheSelectionAndReportsIt() = runTest {
        coEvery { repository.archive(any()) } throws IOException("sin acceso")
        val viewModel = viewModelWith(entry("a", InboxState.PENDING))
        collectState(viewModel)
        viewModel.onToggleSelection("a")

        viewModel.archive(setOf("a"))
        advanceUntilIdle()

        assertEquals(setOf("a"), viewModel.uiState.value.selectedIds)
        assertEquals(R.string.inbox_action_failed, viewModel.messages.first())
    }
}
