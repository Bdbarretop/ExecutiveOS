package com.executiveos.feature.capture

import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.DateOption
import com.executiveos.domain.repository.CaptureDraftRepository
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.InboxRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.repository.TemplateRepository
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickCaptureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inbox = mockk<InboxRepository>()
    private val drafts = FakeDraftRepository()
    private val categories = mockk<CategoryRepository> {
        every { observeCategories() } returns flowOf(emptyList())
    }
    private val templates = mockk<TemplateRepository> {
        every { observeTemplates() } returns flowOf(emptyList())
    }
    private val tasks = mockk<TaskRepository> {
        every { observeOpenTaskTitles() } returns flowOf(emptyList())
    }

    private fun viewModel() = QuickCaptureViewModel(inbox, drafts, categories, templates, tasks)

    private fun TestScope.collectState(viewModel: QuickCaptureViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
    }

    @Test
    fun restoresTheDraftLeftByAnAccidentalClose() = runTest {
        drafts.stored = CaptureDraft(id = "borrador", title = "Idea a medias")
        val viewModel = viewModel()

        collectState(viewModel)

        assertEquals("Idea a medias", viewModel.uiState.value.draft?.title)
    }

    @Test
    fun blankTitleShowsAnErrorAndSavesNothing() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onTitleChange("   ")

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitleError)
        coVerify(exactly = 0) { inbox.capture(any()) }
    }

    @Test
    fun doublePressWhileSavingCapturesOnlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { inbox.capture(any()) } coAnswers {
            gate.await()
            "tarea"
        }
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onTitleChange("Enviar informe")

        viewModel.save()
        viewModel.save()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { inbox.capture(any()) }
    }

    @Test
    fun afterSavingTheFormIsReadyForAnotherCapture() = runTest {
        coEvery { inbox.capture(any()) } returns "tarea"
        val viewModel = viewModel()
        collectState(viewModel)
        val firstId = viewModel.uiState.value.draft?.id
        viewModel.onCategorySelected("trabajo")
        viewModel.onTitleChange("Primera")

        viewModel.save()
        advanceUntilIdle()

        val draft = viewModel.uiState.value.draft!!
        assertEquals("", draft.title)
        assertEquals("trabajo", draft.categoryId)
        assertNotEquals(firstId, draft.id)
        assertEquals("trabajo", drafts.rememberedCategory)
        assertEquals(QuickCaptureEvent.Saved, viewModel.events.first())
    }

    @Test
    fun failedSaveKeepsTheTextAndReportsIt() = runTest {
        coEvery { inbox.capture(any()) } throws IOException("sin espacio")
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onTitleChange("No perder esto")

        viewModel.save()
        advanceUntilIdle()

        assertEquals("No perder esto", viewModel.uiState.value.draft?.title)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(QuickCaptureEvent.SaveFailed, viewModel.events.first())
    }

    @Test
    fun autosavesTheDraftAfterAShortPause() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)

        viewModel.onTitleChange("Escribiendo")
        runCurrent()
        assertNull(drafts.saved.lastOrNull())

        advanceTimeBy(500)
        runCurrent()

        assertEquals("Escribiendo", drafts.saved.last().title)
    }

    @Test
    fun removingTheDateAlsoRemovesTheDeadlineConfirmation() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onDateOptionSelected(DateOption.TODAY)
        viewModel.onDeadlineChanged(true)

        viewModel.onDateOptionSelected(DateOption.NONE)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.draft!!.dateIsDeadline)
    }

    @Test
    fun templateNeverOverwritesWhatWasAlreadyWritten() = runTest {
        val template = CaptureTemplate(
            id = "t",
            name = "Preparar informe",
            titlePrefix = "Preparar informe: ",
            categoryId = "trabajo",
            whatToDo = null,
            expectedOutcome = null,
            nextAction = null,
            estimatedMinutes = null,
            sortOrder = 0,
        )
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onTitleChange("Mi título")

        viewModel.onTemplateSelected(template)
        advanceUntilIdle()

        val draft = viewModel.uiState.value.draft!!
        assertEquals("Mi título", draft.title)
        assertEquals("t", draft.templateId)
        assertEquals("trabajo", draft.categoryId)
    }
}

private class FakeDraftRepository : CaptureDraftRepository {
    var stored = CaptureDraft(id = "inicial")
    val saved = mutableListOf<CaptureDraft>()
    var rememberedCategory: String? = null

    override suspend fun loadDraft(): CaptureDraft = stored

    override suspend fun saveDraft(draft: CaptureDraft) {
        stored = draft
        saved += draft
    }

    override suspend fun rememberCategory(categoryId: String?) {
        rememberedCategory = categoryId
    }

    override val lastProjectId = flowOf<String?>(null)

    override suspend fun rememberProject(projectId: String?) = Unit
}
