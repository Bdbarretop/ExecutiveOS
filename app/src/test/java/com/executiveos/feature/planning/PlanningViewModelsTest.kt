package com.executiveos.feature.planning

import androidx.lifecycle.SavedStateHandle
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.PlanningPeriod
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Miércoles. */
    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val blocks = MutableStateFlow<List<TimeBlock>>(emptyList())
    private val repository = mockk<PlanningRepository>(relaxUnitFun = true) {
        every { observeBlocks() } returns blocks
        every { capacity } returns MutableStateFlow(CapacitySettings())
    }
    private val tasks = mockk<TaskRepository> {
        every { observeTaskList() } returns flowOf(emptyList())
    }

    private fun block(id: String, start: Int, end: Int, day: LocalDate = today) = TimeBlock(
        id = id,
        type = BlockType.DEEP_WORK,
        title = id,
        startAt = day.atTime(start, 0).toInstant(ZoneOffset.UTC),
        endAt = day.atTime(end, 0).toInstant(ZoneOffset.UTC),
    )

    private fun TestScope.planning(): PlanningViewModel {
        val viewModel = PlanningViewModel(repository, tasks, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.editor(vararg arguments: Pair<String, String>): BlockEditorViewModel {
        val viewModel = BlockEditorViewModel(
            SavedStateHandle(mapOf(*arguments)),
            repository,
            tasks,
            clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun theWeekViewCoversSevenDaysAndAdvancesByWeeks() = runTest {
        val viewModel = planning()

        viewModel.selectPeriod(PlanningPeriod.WEEK)
        advanceUntilIdle()
        assertEquals(7, viewModel.uiState.value!!.days.size)
        assertEquals(LocalDate.of(2026, 11, 9), viewModel.uiState.value!!.span.from)

        viewModel.next()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 16), viewModel.uiState.value!!.span.from)
    }

    @Test
    fun openingADayFromTheMonthShowsItsDailyView() = runTest {
        val viewModel = planning()
        viewModel.selectPeriod(PlanningPeriod.MONTH)

        viewModel.openDay(today.plusDays(3))
        advanceUntilIdle()

        assertEquals(PlanningPeriod.DAY, viewModel.uiState.value!!.period)
        assertEquals(today.plusDays(3), viewModel.uiState.value!!.anchor)
    }

    @Test
    fun overlappingBlocksAndDailyOverloadAreReported() = runTest {
        blocks.value = listOf(block("a", 8, 12), block("b", 11, 15), block("c", 16, 17))
        val viewModel = planning()

        val state = viewModel.uiState.value!!

        assertEquals(setOf("a", "b"), state.overlappingIds)
        assertTrue(state.days.single().isOverloaded)
    }

    @Test
    fun movingABlockKeepsItsDurationAndSavesTheNewTime() = runTest {
        val original = block("a", 9, 10)
        val viewModel = planning()

        viewModel.move(original, days = 0, minutes = 30)
        advanceUntilIdle()

        coVerify {
            repository.save(
                match {
                    it.startAt == original.startAt.plusSeconds(1_800) && it.plannedMinutes == 60
                },
            )
        }
    }

    @Test
    fun actualTimeIsSavedOnTheBlock() = runTest {
        val viewModel = planning()

        viewModel.logActual(block("a", 9, 10), 45)
        advanceUntilIdle()

        coVerify { repository.save(match { it.id == "a" && it.actualMinutes == 45 }) }
    }

    @Test
    fun aNewBlockStartsOnTheRequestedDayAtNineByDefault() = runTest {
        val viewModel = editor("date" to "2026-11-13")

        val draft = viewModel.uiState.value.draft!!

        assertEquals(LocalDate.of(2026, 11, 13), draft.date)
        assertEquals(LocalTime.of(9, 0), draft.start)
        assertEquals(LocalTime.of(10, 0), draft.end)
    }

    @Test
    fun aNewBlockForTodayStartsAtTheNextFullHour() = runTest {
        val draft = editor().uiState.value.draft!!

        assertEquals(today, draft.date)
        assertEquals(LocalTime.of(16, 0), draft.start)
    }

    @Test
    fun aBlockWithoutTitleAsksForOneAndIsNotSaved() = runTest {
        val viewModel = editor()

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitleError)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun aValidBlockIsSavedAndTheEditorCloses() = runTest {
        val viewModel = editor()
        viewModel.onEdit { copy(title = "Informe") }

        viewModel.save()

        assertEquals(BlockEditorEvent.Close, viewModel.events.first())
        coVerify { repository.save(match { it.title == "Informe" }) }
    }

    @Test
    fun anExistingBlockIsLoadedForEditing() = runTest {
        blocks.value = listOf(block("a", 9, 11))

        val state = editor("blockId" to "a").uiState.value

        assertFalse(state.isNew)
        assertEquals(LocalTime.of(11, 0), state.draft!!.end)
    }

    @Test
    fun conflictsAndExceedingTheTargetAreWarnedButDoNotPreventSaving() = runTest {
        blocks.value = listOf(block("otro", 9, 15))
        val viewModel = editor("date" to today.toString())
        viewModel.onEdit { copy(title = "Doble", start = LocalTime.of(14, 0), end = LocalTime.of(16, 0)) }
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals(listOf("otro"), state.conflicts.map { it.id })
        assertTrue(state.exceedsTarget)
        viewModel.save()
        assertEquals(BlockEditorEvent.Close, viewModel.events.first())
    }

    @Test
    fun movingTheStartKeepsTheDuration() = runTest {
        val viewModel = editor("date" to "2026-11-13")

        viewModel.onStartSelected(LocalTime.of(14, 30))
        advanceUntilIdle()

        assertEquals(LocalTime.of(15, 30), viewModel.uiState.value.draft!!.end)
    }

    @Test
    fun anEndBeforeTheStartIsReportedWithoutConflicts() = runTest {
        blocks.value = listOf(block("otro", 8, 20))
        val viewModel = editor("date" to today.toString())

        viewModel.onEdit { copy(start = LocalTime.of(12, 0), end = LocalTime.of(11, 0)) }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errors.isNotEmpty())
        assertTrue(viewModel.uiState.value.conflicts.isEmpty())
    }

    @Test
    fun choosingATaskFillsABlankTitleButNotAWrittenOne() = runTest {
        val viewModel = editor()
        val task = TaskReference("t", "Preparar comité", TaskStatus.PENDING)

        viewModel.onTaskSelected(task)
        advanceUntilIdle()
        assertEquals("Preparar comité", viewModel.uiState.value.draft!!.title)

        viewModel.onEdit { copy(title = "Otro título") }
        viewModel.onTaskSelected(task)
        advanceUntilIdle()
        assertEquals("Otro título", viewModel.uiState.value.draft!!.title)
    }
}
