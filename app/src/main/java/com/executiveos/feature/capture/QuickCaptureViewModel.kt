package com.executiveos.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.core.navigation.TaskSection
import com.executiveos.domain.capture.DuplicateDetector
import com.executiveos.domain.capture.SimilarTask
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.Category
import com.executiveos.domain.model.DateOption
import com.executiveos.domain.repository.CaptureDraftRepository
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.InboxRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickCaptureUiState(
    val draft: CaptureDraft? = null,
    val categories: List<Category> = emptyList(),
    val templates: List<CaptureTemplate> = emptyList(),
    val similarTasks: List<SimilarTask> = emptyList(),
    val showTitleError: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface QuickCaptureEvent {
    data object Saved : QuickCaptureEvent
    data object SaveFailed : QuickCaptureEvent
    data class OpenTask(val taskId: String, val section: TaskSection) : QuickCaptureEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val inboxRepository: InboxRepository,
    private val draftRepository: CaptureDraftRepository,
    categoryRepository: CategoryRepository,
    templateRepository: TemplateRepository,
    taskRepository: TaskRepository,
) : ViewModel() {
    private val draft = MutableStateFlow<CaptureDraft?>(null)
    private val titleError = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)
    private val eventChannel = Channel<QuickCaptureEvent>(Channel.BUFFERED)

    val events: Flow<QuickCaptureEvent> = eventChannel.receiveAsFlow()

    private val similarTasks = combine(
        draft.map { it?.title.orEmpty() }
            .distinctUntilChanged()
            .debounce(SIMILARITY_DEBOUNCE_MILLIS),
        taskRepository.observeOpenTaskTitles(),
    ) { title, openTasks -> DuplicateDetector.findSimilar(title, openTasks) }

    private val status = combine(titleError, saving) { error, isSaving -> error to isSaving }

    val uiState: StateFlow<QuickCaptureUiState> = combine(
        draft,
        categoryRepository.observeCategories(),
        templateRepository.observeTemplates(),
        similarTasks,
        status,
    ) { currentDraft, categories, templates, similar, (error, isSaving) ->
        QuickCaptureUiState(currentDraft, categories, templates, similar, error, isSaving)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = QuickCaptureUiState(),
    )

    init {
        viewModelScope.launch { draft.value = draftRepository.loadDraft() }
        viewModelScope.launch {
            draft.filterNotNull()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MILLIS)
                .collect { draftRepository.saveDraft(it) }
        }
    }

    fun onTitleChange(title: String) {
        titleError.value = false
        updateDraft { it.copy(title = title) }
    }

    fun onDetailsChange(details: String) = updateDraft { it.copy(details = details) }

    fun onCategorySelected(categoryId: String) = updateDraft {
        it.copy(categoryId = categoryId.takeUnless { id -> id == it.categoryId })
    }

    fun onDateOptionSelected(option: DateOption) = updateDraft {
        it.copy(
            dateOption = option,
            customDate = it.customDate.takeIf { option == DateOption.CUSTOM },
            dateIsDeadline = it.dateIsDeadline && option != DateOption.NONE,
        )
    }

    fun onCustomDateSelected(date: LocalDate) = updateDraft {
        it.copy(dateOption = DateOption.CUSTOM, customDate = date)
    }

    fun onDeadlineChanged(isDeadline: Boolean) = updateDraft { it.copy(dateIsDeadline = isDeadline) }

    /** Una plantilla solo completa lo que esté vacío: nunca pisa lo ya escrito. */
    fun onTemplateSelected(template: CaptureTemplate) = updateDraft { current ->
        if (current.templateId == template.id) {
            current.copy(templateId = null)
        } else {
            current.copy(
                templateId = template.id,
                title = current.title.ifBlank { template.titlePrefix },
                categoryId = current.categoryId ?: template.categoryId,
            )
        }
    }

    fun save() = persist { QuickCaptureEvent.Saved }

    fun saveAndOpen(section: TaskSection) =
        persist { taskId -> QuickCaptureEvent.OpenTask(taskId, section) }

    /** Guarda de inmediato lo pendiente, para no perderlo al salir de la pantalla. */
    fun flushDraft(onComplete: () -> Unit = {}) {
        val current = draft.value
        viewModelScope.launch {
            if (current != null) draftRepository.saveDraft(current)
            onComplete()
        }
    }

    private fun persist(eventFor: (taskId: String) -> QuickCaptureEvent) {
        val current = draft.value ?: return
        if (saving.value) return
        if (current.title.isBlank()) {
            titleError.value = true
            return
        }
        saving.value = true
        viewModelScope.launch {
            try {
                val taskId = inboxRepository.capture(current)
                draftRepository.rememberCategory(current.categoryId)
                val next = CaptureDraft(
                    id = UUID.randomUUID().toString(),
                    categoryId = current.categoryId,
                )
                draft.value = next
                draftRepository.saveDraft(next)
                eventChannel.send(eventFor(taskId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                eventChannel.send(QuickCaptureEvent.SaveFailed)
            } finally {
                saving.value = false
            }
        }
    }

    private fun updateDraft(transform: (CaptureDraft) -> CaptureDraft) {
        draft.update { current -> current?.let(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val AUTOSAVE_DEBOUNCE_MILLIS = 400L
        const val SIMILARITY_DEBOUNCE_MILLIS = 300L
    }
}
