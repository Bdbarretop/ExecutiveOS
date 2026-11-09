package com.executiveos.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.executiveos.core.navigation.TemplateEditorRoute
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.Category
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    repository: TemplateRepository,
) : ViewModel() {
    val templates: StateFlow<List<CaptureTemplate>?> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
}

data class TemplateEditorUiState(
    val template: CaptureTemplate? = null,
    val categories: List<Category> = emptyList(),
    val showNameError: Boolean = false,
)

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TemplateRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TemplateEditorRoute>()
    private val template = MutableStateFlow<CaptureTemplate?>(null)
    private val nameError = MutableStateFlow(false)

    val uiState: StateFlow<TemplateEditorUiState> = combine(
        template,
        categoryRepository.observeCategories(),
        nameError,
    ) { current, categories, error ->
        TemplateEditorUiState(current, categories, error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TemplateEditorUiState(),
    )

    init {
        viewModelScope.launch {
            template.value = repository.observeTemplate(route.templateId).first()
        }
    }

    fun onEdit(transform: CaptureTemplate.() -> CaptureTemplate) {
        nameError.value = false
        template.update { current -> current?.transform() }
    }

    fun save(onSaved: () -> Unit) {
        val current = template.value ?: return
        if (current.name.isBlank()) {
            nameError.value = true
            return
        }
        viewModelScope.launch {
            repository.save(current)
            onSaved()
        }
    }
}
