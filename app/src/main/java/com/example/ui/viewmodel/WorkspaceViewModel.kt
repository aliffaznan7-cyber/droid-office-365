package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ooxml.DocxEngine
import com.example.core.ooxml.SpreadsheetFormulaEngine
import com.example.core.ooxml.XlsxEngine
import com.example.core.pdf.PdfPageDimension
import com.example.core.pdf.PdfTextExtractor
import com.example.core.shortcuts.ShortcutCommand
import com.example.data.local.AutosaveEntity
import com.example.data.local.DocuDeskDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfMetadataEntity
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentFormat
import com.example.domain.model.DocumentTab
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxCitation
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxMargins
import com.example.domain.model.DocxMarginsPreset
import com.example.domain.model.DocxNoteKind
import com.example.domain.model.DocxOrientation
import com.example.domain.model.DocxPaperSize
import com.example.domain.model.DocxPaperTone
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxScriptMode
import com.example.domain.model.DocxTextRun
import com.example.domain.model.DocxViewMode
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.NormalizedRect
import com.example.domain.model.PdfAnnotation
import com.example.domain.model.PdfAnnotationType
import com.example.domain.model.PdfEditorTool
import com.example.domain.model.PdfSearchHit
import com.example.domain.model.PdfTextRun
import com.example.domain.model.RibbonTab
import com.example.domain.model.SignaturePreset
import com.example.domain.model.WorkspaceLoadState
import com.example.domain.model.XlsxCell
import com.example.domain.model.XlsxWorkbookModel
import com.example.domain.model.XlsxWorksheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface UndoOp {
    data class AddedAnnotation(val annotation: PdfAnnotation) : UndoOp
    data class DeletedAnnotation(val annotation: PdfAnnotation) : UndoOp
}

enum class WordDialogRequest {
    CUSTOM_MARGINS,
    HEADER_FOOTER,
    HYPERLINK,
    COMMENT,
    DRAWING_CANVAS,
    TEXT_BOX,
    EQUATION,
    FOOTNOTE,
    ENDNOTE,
    CITATION
}

data class WorkspaceUiState(
    val loadState: WorkspaceLoadState = WorkspaceLoadState.Loading,
    val openTabs: List<DocumentTab> = emptyList(),
    val activeTabId: String? = null,
    val activeRibbonTab: RibbonTab = RibbonTab.HOME,
    val isRibbonExpanded: Boolean = true,
    val isNavPaneOpen: Boolean = true,
    val isInspectorOpen: Boolean = false,
    val isSearchVisible: Boolean = false,
    val isDarkCanvas: Boolean = false,
    val statusBanner: String? = null,
    val showShortcutsModal: Boolean = false,
    val showNewDocDialog: Boolean = false,
    // PDF state
    val pdfDimensions: List<PdfPageDimension> = emptyList(),
    val activePdfTool: PdfEditorTool = PdfEditorTool.BROWSE,
    val activeInkColorArgb: Long = 0xFF0F548C,
    val activeHighlightColorArgb: Long = 0xFFFACC15,
    val activeStrokeWidthNormalized: Float = 0.0042f,
    val activeFontSizeSp: Float = 13f,
    val pageTextRuns: Map<Int, List<PdfTextRun>> = emptyMap(),
    val selectedPdfText: String = "",
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val pendingSignature: SignaturePreset? = null,
    val showSignaturePad: Boolean = false,
    // DOCX Word Processor state
    val activeDocxModel: DocxDocumentModel? = null,
    val selectedDocxBlockId: String? = null,
    val docxViewMode: DocxViewMode = DocxViewMode.PRINT_LAYOUT,
    val docxShowRulers: Boolean = true,
    val docxZoomScale: Float = 1.0f,
    val docxPaperTone: DocxPaperTone = DocxPaperTone.CRISP_WHITE,
    val docxFocusMode: Boolean = false,
    val showDocxFindReplaceBar: Boolean = false,
    val docxFindReplaceMode: Boolean = false,
    val docxFindQuery: String = "",
    val docxReplaceQuery: String = "",
    val docxFindMatchedBlockIds: List<String> = emptyList(),
    val docxFindMatchCursor: Int = 0,
    val showWordReviewPanel: Boolean = false,
    val activeWordDialog: WordDialogRequest? = null,
    // XLSX state
    val activeXlsxModel: XlsxWorkbookModel? = null,
    val selectedCell: Pair<Int, Int> = 0 to 0,
    val formulaBarInput: String = ""
) {
    val activeTab: DocumentTab?
        get() = openTabs.firstOrNull { it.documentId == activeTabId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DocuDeskDatabase.getInstance(application)
    val repository = DocumentRepository(
        context = application,
        documentDao = database.documentDao(),
        annotationDao = database.annotationDao(),
        autosaveDao = database.autosaveDao(),
        signatureDao = database.signatureDao(),
        pdfRenderEngine = com.example.core.pdf.PdfRenderEngine()
    )

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    val libraryDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSignatures: StateFlow<List<SignaturePreset>> = repository.savedSignatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeDocIdFlow = MutableStateFlow<String?>(null)

    val activeAnnotations: StateFlow<List<PdfAnnotation>> = activeDocIdFlow
        .flatMapLatest { id -> if (id != null) repository.observeAnnotations(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePdfMetadata: StateFlow<PdfMetadataEntity?> = activeDocIdFlow
        .flatMapLatest { id -> if (id != null) repository.observePdfMetadata(id) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeAutosaves: StateFlow<List<AutosaveEntity>> = activeDocIdFlow
        .flatMapLatest { id -> if (id != null) repository.observeAutosaves(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val undoStacks = mutableMapOf<String, ArrayDeque<UndoOp>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<UndoOp>>()
    private val docxUndoStacks = mutableMapOf<String, ArrayDeque<DocxDocumentModel>>()
    private val docxRedoStacks = mutableMapOf<String, ArrayDeque<DocxDocumentModel>>()
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeededWorkspace()
            _uiState.update { it.copy(loadState = WorkspaceLoadState.Ready()) }
            val seededPart1 = repository.getDocumentById("seed_pdf_part1")
            if (seededPart1 != null) {
                openDocument(seededPart1)
            }
        }
    }

    fun openDocument(entity: DocumentEntity) {
        viewModelScope.launch {
            repository.touchLastOpened(entity.id)
            val existingTab = _uiState.value.openTabs.firstOrNull { it.documentId == entity.id }
            if (existingTab == null) {
                val newTab = DocumentTab(
                    documentId = entity.id,
                    title = entity.title,
                    format = entity.format,
                    filePath = entity.filePath
                )
                _uiState.update { state ->
                    state.copy(
                        openTabs = state.openTabs + newTab,
                        activeTabId = entity.id,
                        activeRibbonTab = if (entity.format == DocumentFormat.PDF) {
                            RibbonTab.PDF_ANNOTATE
                        } else {
                            RibbonTab.HOME
                        }
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        activeTabId = entity.id,
                        activeRibbonTab = if (entity.format == DocumentFormat.PDF) {
                            RibbonTab.PDF_ANNOTATE
                        } else if (state.activeRibbonTab == RibbonTab.PDF_ANNOTATE) {
                            RibbonTab.HOME
                        } else {
                            state.activeRibbonTab
                        }
                    )
                }
            }
            activeDocIdFlow.value = entity.id
            loadActiveDocumentPayload(entity)
        }
    }

    fun switchTab(documentId: String) {
        viewModelScope.launch {
            val tab = _uiState.value.openTabs.firstOrNull { it.documentId == documentId } ?: return@launch
            val doc = repository.getDocumentById(documentId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    activeTabId = documentId,
                    activeRibbonTab = when {
                        tab.format == DocumentFormat.PDF -> RibbonTab.PDF_ANNOTATE
                        state.activeRibbonTab == RibbonTab.PDF_ANNOTATE -> RibbonTab.HOME
                        tab.format != DocumentFormat.DOCX && state.activeRibbonTab == RibbonTab.REFERENCES -> RibbonTab.HOME
                        else -> state.activeRibbonTab
                    }
                )
            }
            activeDocIdFlow.value = documentId
            loadActiveDocumentPayload(doc)
        }
    }

    fun closeTab(documentId: String) {
        val tabs = _uiState.value.openTabs.filterNot { it.documentId == documentId }
        val nextActive = if (_uiState.value.activeTabId == documentId) {
            tabs.lastOrNull()?.documentId
        } else {
            _uiState.value.activeTabId
        }
        _uiState.update {
            it.copy(
                openTabs = tabs,
                activeTabId = nextActive,
                pdfDimensions = if (nextActive == null) emptyList() else it.pdfDimensions,
                activeDocxModel = if (nextActive == null) null else it.activeDocxModel,
                activeXlsxModel = if (nextActive == null) null else it.activeXlsxModel,
                docxFocusMode = if (nextActive == null) false else it.docxFocusMode
            )
        }
        activeDocIdFlow.value = nextActive
        if (nextActive != null) {
            viewModelScope.launch {
                repository.getDocumentById(nextActive)?.let { loadActiveDocumentPayload(it) }
            }
        }
    }

    private suspend fun loadActiveDocumentPayload(entity: DocumentEntity) {
        val file = File(entity.filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(statusBanner = "File missing on disk: ${entity.fileName}") }
            return
        }
        when (entity.format) {
            DocumentFormat.PDF -> {
                val dims = runCatching { repository.pdfRenderEngine.openDocument(file) }
                    .getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        pdfDimensions = dims,
                        pageTextRuns = emptyMap(),
                        searchHits = emptyList(),
                        selectedPdfText = ""
                    )
                }
                if (dims.isNotEmpty()) {
                    extractPageText(file, _uiState.value.activeTab?.currentPage ?: 0)
                }
            }
            DocumentFormat.DOCX -> {
                val mediaDir = File(getApplication<Application>().filesDir, "workspace_media/${entity.id.take(8)}")
                val model = runCatching { DocxEngine.loadDocument(entity.id, file, mediaDir) }.getOrNull()
                _uiState.update {
                    it.copy(
                        activeDocxModel = model,
                        selectedDocxBlockId = model?.blocks?.firstOrNull()?.id
                    )
                }
            }
            DocumentFormat.XLSX -> {
                val wb = runCatching { XlsxEngine.loadWorkbook(entity.id, file) }.getOrNull()
                val firstCell = wb?.worksheets?.firstOrNull()?.cells?.get(0 to 0)
                _uiState.update {
                    it.copy(
                        activeXlsxModel = wb,
                        selectedCell = 0 to 0,
                        formulaBarInput = firstCell?.rawInput ?: ""
                    )
                }
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusBanner = "Importing document...") }
            repository.importFromUri(uri)
                .onSuccess { doc ->
                    _uiState.update { it.copy(statusBanner = "Imported ${doc.title}") }
                    openDocument(doc)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusBanner = "Import failed: ${err.message}") }
                }
        }
    }

    fun createNewDocument(format: DocumentFormat, title: String) {
        viewModelScope.launch {
            val doc = repository.createNewDocument(format, title)
            _uiState.update { it.copy(showNewDocDialog = false, statusBanner = "Created $title") }
            openDocument(doc)
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            closeTab(documentId)
            repository.deleteDocument(documentId)
            _uiState.update { it.copy(statusBanner = "Removed document from local workspace") }
        }
    }

    fun toggleOfflinePin(documentId: String, currentPinned: Boolean) {
        viewModelScope.launch {
            repository.toggleOfflinePin(documentId, !currentPinned)
        }
    }

    // Ribbon & Pane Toggles
    fun selectRibbonTab(tab: RibbonTab) {
        _uiState.update { it.copy(activeRibbonTab = tab, isRibbonExpanded = true) }
    }

    fun toggleRibbonExpanded() {
        _uiState.update { it.copy(isRibbonExpanded = !it.isRibbonExpanded) }
    }

    fun toggleNavPane() {
        _uiState.update { it.copy(isNavPaneOpen = !it.isNavPaneOpen) }
    }

    fun toggleInspectorPane() {
        _uiState.update { it.copy(isInspectorOpen = !it.isInspectorOpen) }
    }

    fun toggleDarkCanvas() {
        _uiState.update { it.copy(isDarkCanvas = !it.isDarkCanvas) }
    }

    fun toggleSearchBar() {
        val tab = _uiState.value.activeTab
        if (tab?.format == DocumentFormat.DOCX) {
            _uiState.update {
                it.copy(
                    showDocxFindReplaceBar = !it.showDocxFindReplaceBar,
                    docxFindReplaceMode = false
                )
            }
        } else {
            _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible) }
        }
    }

    fun setShowShortcutsModal(show: Boolean) {
        _uiState.update { it.copy(showShortcutsModal = show) }
    }

    fun setShowNewDocDialog(show: Boolean) {
        _uiState.update { it.copy(showNewDocDialog = show) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    // PDF Navigation, Zoom, Search & Text Selection
    fun setPdfCurrentPage(pageIndex: Int) {
        val tab = _uiState.value.activeTab ?: return
        if (tab.currentPage == pageIndex) return
        updateActiveTab { it.copy(currentPage = pageIndex) }
        viewModelScope.launch {
            repository.updatePdfViewport(tab.documentId, pageIndex, tab.zoomScale)
            val file = File(tab.filePath)
            extractPageText(file, pageIndex)
            val total = _uiState.value.pdfDimensions.size
            val renderWidth = (1080 * tab.zoomScale).toInt().coerceIn(720, 2400)
            repository.pdfRenderEngine.prefetchAdjacentPages(
                file = file,
                documentKey = tab.documentId,
                currentPage = pageIndex,
                totalPages = total,
                targetWidthPx = renderWidth
            )
        }
    }

    fun setPdfZoom(zoom: Float) {
        val clamped = zoom.coerceIn(0.5f, 4.0f)
        val tab = _uiState.value.activeTab ?: return
        if (tab.format == DocumentFormat.DOCX) {
            setDocxZoom(clamped)
            return
        }
        updateActiveTab { it.copy(zoomScale = clamped) }
        viewModelScope.launch {
            repository.updatePdfViewport(tab.documentId, tab.currentPage, clamped)
        }
    }

    fun selectPdfTool(tool: PdfEditorTool) {
        _uiState.update {
            it.copy(
                activePdfTool = tool,
                pendingSignature = if (tool != PdfEditorTool.SIGNATURE) null else it.pendingSignature
            )
        }
    }

    fun setInkColor(colorArgb: Long) {
        _uiState.update { it.copy(activeInkColorArgb = colorArgb) }
    }

    fun setHighlightColor(colorArgb: Long) {
        _uiState.update { it.copy(activeHighlightColorArgb = colorArgb) }
    }

    fun setStrokeWidth(normalized: Float) {
        _uiState.update { it.copy(activeStrokeWidthNormalized = normalized.coerceIn(0.0015f, 0.014f)) }
    }

    fun setShowSignaturePad(show: Boolean) {
        _uiState.update { it.copy(showSignaturePad = show) }
    }

    fun saveSignaturePreset(label: String, strokes: List<List<NormalizedPoint>>) {
        val color = _uiState.value.activeInkColorArgb
        viewModelScope.launch {
            repository.saveSignaturePreset(label, strokes, color)
            _uiState.update {
                it.copy(
                    showSignaturePad = false,
                    statusBanner = "Saved reusable vector signature '$label'"
                )
            }
        }
    }

    fun armSignaturePlacement(preset: SignaturePreset) {
        _uiState.update {
            it.copy(
                pendingSignature = preset,
                activePdfTool = PdfEditorTool.SIGNATURE,
                statusBanner = "Tap any PDF page to place '${preset.label}'"
            )
        }
    }

    fun deleteSignaturePreset(id: String) {
        viewModelScope.launch { repository.deleteSignaturePreset(id) }
    }

    fun setSelectedPdfText(text: String) {
        _uiState.update { it.copy(selectedPdfText = text) }
    }

    private fun extractPageText(file: File, pageIndex: Int) {
        if (_uiState.value.pageTextRuns.containsKey(pageIndex)) return
        viewModelScope.launch {
            val runs = PdfTextExtractor.extractPageTextRuns(file, pageIndex)
            _uiState.update { state ->
                state.copy(pageTextRuns = state.pageTextRuns + (pageIndex to runs))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val tab = _uiState.value.activeTab ?: return
        if (tab.format != DocumentFormat.PDF) return
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchHits = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val hits = PdfTextExtractor.searchDocument(File(tab.filePath), query)
            _uiState.update { it.copy(searchHits = hits, isSearching = false) }
        }
    }

    // PDF Annotation CRUD + Undo/Redo
    fun addPdfInkStroke(pageIndex: Int, points: List<NormalizedPoint>) {
        if (points.size < 2) return
        val tab = _uiState.value.activeTab ?: return
        val ann = PdfAnnotation(
            id = UUID.randomUUID().toString(),
            documentId = tab.documentId,
            pageIndex = pageIndex,
            type = PdfAnnotationType.INK,
            colorArgb = _uiState.value.activeInkColorArgb,
            strokeWidthNormalized = _uiState.value.activeStrokeWidthNormalized,
            points = points
        )
        persistNewAnnotation(ann)
    }

    fun addPdfHighlight(pageIndex: Int, rect: NormalizedRect, isUnderline: Boolean = false) {
        val tab = _uiState.value.activeTab ?: return
        val ann = PdfAnnotation(
            id = UUID.randomUUID().toString(),
            documentId = tab.documentId,
            pageIndex = pageIndex,
            type = if (isUnderline) PdfAnnotationType.UNDERLINE else PdfAnnotationType.HIGHLIGHT,
            colorArgb = _uiState.value.activeHighlightColorArgb,
            strokeWidthNormalized = 0.0025f,
            bounds = rect
        )
        persistNewAnnotation(ann)
    }

    fun addPdfTextBox(pageIndex: Int, rect: NormalizedRect, text: String) {
        if (text.isBlank()) return
        val tab = _uiState.value.activeTab ?: return
        val ann = PdfAnnotation(
            id = UUID.randomUUID().toString(),
            documentId = tab.documentId,
            pageIndex = pageIndex,
            type = PdfAnnotationType.TEXT_BOX,
            colorArgb = _uiState.value.activeInkColorArgb,
            bounds = rect,
            textContent = text.trim(),
            fontSizeSp = _uiState.value.activeFontSizeSp
        )
        persistNewAnnotation(ann)
    }

    fun stampPendingSignature(pageIndex: Int, normX: Float, normY: Float) {
        val preset = _uiState.value.pendingSignature ?: return
        val tab = _uiState.value.activeTab ?: return
        val boxW = 0.24f
        val boxH = 0.085f
        val left = (normX - boxW / 2f).coerceIn(0.02f, 0.98f - boxW)
        val top = (normY - boxH / 2f).coerceIn(0.02f, 0.98f - boxH)

        viewModelScope.launch {
            preset.strokes.forEach { stroke ->
                val mapped = stroke.map { pt ->
                    NormalizedPoint(
                        x = left + pt.x * boxW,
                        y = top + pt.y * boxH,
                        pressure = pt.pressure
                    )
                }
                if (mapped.size >= 2) {
                    val ann = PdfAnnotation(
                        id = UUID.randomUUID().toString(),
                        documentId = tab.documentId,
                        pageIndex = pageIndex,
                        type = PdfAnnotationType.SIGNATURE,
                        colorArgb = preset.colorArgb,
                        strokeWidthNormalized = 0.0036f,
                        points = mapped
                    )
                    repository.saveAnnotation(ann)
                    pushUndo(tab.documentId, UndoOp.AddedAnnotation(ann))
                }
            }
            markTabDirty(tab.documentId, true)
            _uiState.update {
                it.copy(
                    pendingSignature = null,
                    activePdfTool = PdfEditorTool.BROWSE,
                    statusBanner = "Placed signature '${preset.label}' on page ${pageIndex + 1}"
                )
            }
        }
    }

    fun eraseAnnotationNear(pageIndex: Int, normX: Float, normY: Float) {
        val tab = _uiState.value.activeTab ?: return
        val target = activeAnnotations.value.lastOrNull { ann ->
            if (ann.pageIndex != pageIndex || ann.isFlattened) return@lastOrNull false
            val hitBounds = ann.bounds?.let { b ->
                normX in (b.left - 0.025f)..(b.right + 0.025f) &&
                    normY in (b.top - 0.025f)..(b.bottom + 0.025f)
            } ?: false
            val hitStroke = ann.points.any { pt ->
                kotlin.math.abs(pt.x - normX) < 0.03f && kotlin.math.abs(pt.y - normY) < 0.03f
            }
            hitBounds || hitStroke
        } ?: return

        viewModelScope.launch {
            repository.deleteAnnotation(tab.documentId, target.id)
            pushUndo(tab.documentId, UndoOp.DeletedAnnotation(target))
            markTabDirty(tab.documentId, true)
        }
    }

    private fun persistNewAnnotation(ann: PdfAnnotation) {
        viewModelScope.launch {
            repository.saveAnnotation(ann)
            pushUndo(ann.documentId, UndoOp.AddedAnnotation(ann))
            markTabDirty(ann.documentId, true)
        }
    }

    fun undo() {
        val tab = _uiState.value.activeTab ?: return
        if (tab.format == DocumentFormat.DOCX) {
            val current = _uiState.value.activeDocxModel ?: return
            val stack = docxUndoStacks[tab.documentId] ?: return
            val prev = stack.removeLastOrNull() ?: return
            docxRedoStacks.getOrPut(tab.documentId) { ArrayDeque() }.addLast(current)
            _uiState.update { it.copy(activeDocxModel = prev) }
            markTabDirty(tab.documentId, true)
            return
        }
        val stack = undoStacks[tab.documentId] ?: return
        val op = stack.removeLastOrNull() ?: return
        val redo = redoStacks.getOrPut(tab.documentId) { ArrayDeque() }
        viewModelScope.launch {
            when (op) {
                is UndoOp.AddedAnnotation -> {
                    repository.deleteAnnotation(tab.documentId, op.annotation.id)
                    redo.addLast(op)
                }
                is UndoOp.DeletedAnnotation -> {
                    repository.saveAnnotation(op.annotation)
                    redo.addLast(op)
                }
            }
        }
    }

    fun redo() {
        val tab = _uiState.value.activeTab ?: return
        if (tab.format == DocumentFormat.DOCX) {
            val current = _uiState.value.activeDocxModel ?: return
            val stack = docxRedoStacks[tab.documentId] ?: return
            val next = stack.removeLastOrNull() ?: return
            docxUndoStacks.getOrPut(tab.documentId) { ArrayDeque() }.addLast(current)
            _uiState.update { it.copy(activeDocxModel = next) }
            markTabDirty(tab.documentId, true)
            return
        }
        val stack = redoStacks[tab.documentId] ?: return
        val op = stack.removeLastOrNull() ?: return
        val undo = undoStacks.getOrPut(tab.documentId) { ArrayDeque() }
        viewModelScope.launch {
            when (op) {
                is UndoOp.AddedAnnotation -> {
                    repository.saveAnnotation(op.annotation)
                    undo.addLast(op)
                }
                is UndoOp.DeletedAnnotation -> {
                    repository.deleteAnnotation(tab.documentId, op.annotation.id)
                    undo.addLast(op)
                }
            }
        }
    }

    private fun pushUndo(docId: String, op: UndoOp) {
        val stack = undoStacks.getOrPut(docId) { ArrayDeque() }
        if (stack.size >= 40) stack.removeFirst()
        stack.addLast(op)
        redoStacks[docId]?.clear()
    }

    fun flattenAndSaveActivePdf(exportUri: Uri? = null) {
        val tab = _uiState.value.activeTab ?: return
        if (tab.format != DocumentFormat.PDF) return
        viewModelScope.launch {
            _uiState.update { it.copy(statusBanner = "Flattening vector annotations into PDF...") }
            repository.flattenAndSavePdf(tab.documentId, exportUri)
                .onSuccess {
                    markTabDirty(tab.documentId, false)
                    val dims = runCatching {
                        repository.pdfRenderEngine.openDocument(File(tab.filePath))
                    }.getOrDefault(_uiState.value.pdfDimensions)
                    _uiState.update {
                        it.copy(
                            pdfDimensions = dims,
                            statusBanner = if (exportUri != null) {
                                "Flattened & exported lossless vector PDF"
                            } else {
                                "Saved & flattened lossless vector PDF"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusBanner = "PDF flatten warning: ${err.message}")
                    }
                }
        }
    }

    // ==================== FULL DESKTOP WORD PROCESSOR OPERATIONS ====================

    private fun mutateDocx(
        recordUndo: Boolean = true,
        autosaveReason: String? = null,
        selectBlockId: String? = null,
        banner: String? = null,
        transform: (DocxDocumentModel) -> DocxDocumentModel
    ) {
        val current = _uiState.value.activeDocxModel ?: return
        if (recordUndo) {
            val stack = docxUndoStacks.getOrPut(current.documentId) { ArrayDeque() }
            if (stack.size >= 40) stack.removeFirst()
            stack.addLast(current)
            docxRedoStacks[current.documentId]?.clear()
        }
        val next = transform(current)
        _uiState.update { state ->
            state.copy(
                activeDocxModel = next,
                selectedDocxBlockId = selectBlockId ?: state.selectedDocxBlockId,
                statusBanner = banner ?: state.statusBanner
            )
        }
        markTabDirty(current.documentId, true)
        if (autosaveReason != null) {
            scheduleDebouncedAutosave(current.documentId, autosaveReason)
        }
    }

    fun openWordDialog(dialog: WordDialogRequest?) {
        _uiState.update { it.copy(activeWordDialog = dialog) }
    }

    fun selectDocxBlock(blockId: String) {
        _uiState.update { it.copy(selectedDocxBlockId = blockId) }
    }

    fun updateDocxParagraphText(blockId: String, newText: String) {
        mutateDocx(recordUndo = true, autosaveReason = "Word edit autosave") { model ->
            DocxDocumentMutator.updateParagraphText(model, blockId, newText)
        }
    }

    fun setDocxFontFamily(fontFamily: String) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraphRuns(model, targetId) { it.copy(fontFamily = fontFamily) }
        }
    }

    fun setDocxFontSize(fontSizeSp: Float) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        val clamped = fontSizeSp.coerceIn(8f, 48f)
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraphRuns(model, targetId) { it.copy(fontSizeSp = clamped) }
        }
    }

    fun toggleDocxFormatting(
        bold: Boolean? = null,
        italic: Boolean? = null,
        underline: Boolean? = null,
        strikethrough: Boolean? = null
    ) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraphRuns(model, targetId) { run ->
                run.copy(
                    bold = if (bold == true) !run.bold else run.bold,
                    italic = if (italic == true) !run.italic else run.italic,
                    underline = if (underline == true) !run.underline else run.underline,
                    strikethrough = if (strikethrough == true) !run.strikethrough else run.strikethrough
                )
            }
        }
    }

    fun toggleDocxScriptMode(mode: DocxScriptMode) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraphRuns(model, targetId) { run ->
                val nextMode = if (run.scriptMode == mode) DocxScriptMode.NORMAL else mode
                run.copy(scriptMode = nextMode)
            }
        }
    }

    fun setDocxTextColor(colorHex: String?) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraphRuns(model, targetId) { it.copy(colorHex = colorHex) }
        }
    }

    fun setDocxHighlightColor(colorHex: String?) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraphRuns(model, targetId) { it.copy(highlightColorHex = colorHex) }
        }
    }

    fun clearDocxFormatting() {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx(banner = "Cleared formatting on selected block") { model ->
            DocxDocumentMutator.clearFormatting(model, targetId)
        }
    }

    fun setDocxParagraphStyle(style: DocxParagraphStyle) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.applyParagraphStyle(model, targetId, style)
        }
    }

    fun setDocxAlignment(alignment: DocxAlignment) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraph(model, targetId) { it.copy(alignment = alignment) }
        }
    }

    fun setDocxLineSpacing(multiplier: Float) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraph(model, targetId) {
                it.copy(lineSpacingMultiplier = multiplier.coerceIn(1.0f, 2.5f))
            }
        }
    }

    fun adjustDocxParagraphSpacing(beforeDeltaPt: Float, afterDeltaPt: Float) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraph(model, targetId) { para ->
                para.copy(
                    spaceBeforePt = (para.spaceBeforePt + beforeDeltaPt).coerceIn(0f, 48f),
                    spaceAfterPt = (para.spaceAfterPt + afterDeltaPt).coerceIn(0f, 48f)
                )
            }
        }
    }

    fun adjustDocxIndent(deltaPt: Float, deltaListLevel: Int = 0) {
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx { model ->
            DocxDocumentMutator.updateSelectedParagraph(model, targetId) { para ->
                val isList = para.style == DocxParagraphStyle.BULLET ||
                    para.style == DocxParagraphStyle.NUMBERED ||
                    para.style == DocxParagraphStyle.MULTILEVEL
                para.copy(
                    leftIndentPt = (para.leftIndentPt + deltaPt).coerceIn(0f, 144f),
                    listLevel = if (isList) (para.listLevel + deltaListLevel).coerceIn(0, 2) else para.listLevel
                )
            }
        }
    }

    // Find & Replace
    fun openDocxFindReplace(replaceMode: Boolean) {
        _uiState.update {
            it.copy(
                showDocxFindReplaceBar = true,
                docxFindReplaceMode = replaceMode
            )
        }
    }

    fun closeDocxFindReplace() {
        _uiState.update {
            it.copy(
                showDocxFindReplaceBar = false,
                docxFindMatchedBlockIds = emptyList()
            )
        }
    }

    fun updateDocxFindQuery(query: String) {
        val model = _uiState.value.activeDocxModel
        val matches = if (model != null) DocxDocumentMutator.findMatches(model, query) else emptyList()
        _uiState.update {
            it.copy(
                docxFindQuery = query,
                docxFindMatchedBlockIds = matches,
                docxFindMatchCursor = 0,
                selectedDocxBlockId = matches.firstOrNull() ?: it.selectedDocxBlockId
            )
        }
    }

    fun updateDocxReplaceQuery(query: String) {
        _uiState.update { it.copy(docxReplaceQuery = query) }
    }

    fun findNextDocxMatch() {
        val matches = _uiState.value.docxFindMatchedBlockIds
        if (matches.isEmpty()) return
        val nextIdx = (_uiState.value.docxFindMatchCursor + 1) % matches.size
        _uiState.update {
            it.copy(
                docxFindMatchCursor = nextIdx,
                selectedDocxBlockId = matches[nextIdx]
            )
        }
    }

    fun replaceCurrentDocxMatch() {
        val matches = _uiState.value.docxFindMatchedBlockIds
        val q = _uiState.value.docxFindQuery
        val r = _uiState.value.docxReplaceQuery
        if (matches.isEmpty() || q.isEmpty()) return
        val targetId = matches[_uiState.value.docxFindMatchCursor.coerceIn(matches.indices)]
        mutateDocx { model -> DocxDocumentMutator.replaceOneInBlock(model, targetId, q, r) }
        updateDocxFindQuery(q)
    }

    fun replaceAllDocxMatches() {
        val q = _uiState.value.docxFindQuery
        val r = _uiState.value.docxReplaceQuery
        if (q.isEmpty()) return
        var replacedCount = 0
        mutateDocx { model ->
            val (next, count) = DocxDocumentMutator.replaceAll(model, q, r)
            replacedCount = count
            next
        }
        _uiState.update {
            it.copy(
                docxFindMatchedBlockIds = emptyList(),
                statusBanner = "Replaced $replacedCount occurrences of '$q'"
            )
        }
    }

    // Insert Operations
    fun insertDocxParagraph(style: DocxParagraphStyle = DocxParagraphStyle.NORMAL) {
        val newPara = DocxDocumentMutator.createParagraph(style)
        val afterId = _uiState.value.selectedDocxBlockId
        mutateDocx(selectBlockId = newPara.id) { model ->
            DocxDocumentMutator.insertBlockAfter(model, afterId, newPara)
        }
    }

    fun insertDocxPageBreak() {
        val br = DocxBlock.PageBreak(UUID.randomUUID().toString())
        val afterId = _uiState.value.selectedDocxBlockId
        mutateDocx(selectBlockId = br.id, banner = "Inserted Page Break (Ctrl+Enter)") { model ->
            DocxDocumentMutator.insertBlockAfter(model, afterId, br)
        }
    }

    fun insertDocxTable(rows: Int = 3, cols: Int = 3) {
        val newTable = DocxDocumentMutator.createTable(rows, cols)
        val afterId = _uiState.value.selectedDocxBlockId
        mutateDocx(selectBlockId = newTable.id, banner = "Inserted ${rows}×${cols} table") { model ->
            DocxDocumentMutator.insertBlockAfter(model, afterId, newTable)
        }
    }

    fun updateDocxTableCell(tableId: String, rowIdx: Int, colIdx: Int, value: String) {
        mutateDocx(recordUndo = false, autosaveReason = "Table edit autosave") { model ->
            val updatedBlocks = model.blocks.map { block ->
                if (block is DocxBlock.Table && block.id == tableId) {
                    val rows = block.rows.mapIndexed { r, row ->
                        if (r == rowIdx) row.mapIndexed { c, cell -> if (c == colIdx) value else cell }
                        else row
                    }
                    block.copy(rows = rows)
                } else block
            }
            model.copy(blocks = updatedBlocks)
        }
    }

    fun modifyDocxTableStructure(tableId: String, addRow: Boolean = false, addCol: Boolean = false, deleteLastRow: Boolean = false, deleteLastCol: Boolean = false) {
        mutateDocx { model ->
            val updated = model.blocks.map { block ->
                if (block is DocxBlock.Table && block.id == tableId) {
                    val rows = block.rows.toMutableList()
                    val colsCount = rows.firstOrNull()?.size ?: 3
                    when {
                        addRow -> rows.add(List(colsCount) { "Cell ${rows.size},${it + 1}" })
                        deleteLastRow && rows.size > 1 -> rows.removeAt(rows.lastIndex)
                        addCol -> {
                            for (i in rows.indices) {
                                rows[i] = rows[i] + if (i == 0) "Header ${colsCount + 1}" else "Value"
                            }
                        }
                        deleteLastCol && colsCount > 1 -> {
                            for (i in rows.indices) {
                                rows[i] = rows[i].dropLast(1)
                            }
                        }
                    }
                    block.copy(rows = rows)
                } else block
            }
            model.copy(blocks = updated)
        }
    }

    fun insertDocxImageFromUri(uri: Uri) {
        val model = _uiState.value.activeDocxModel ?: return
        viewModelScope.launch {
            repository.copyImageUriToMediaFile(model.documentId, uri)
                .onSuccess { dest ->
                    val img = DocxBlock.ImageBlock(
                        id = UUID.randomUUID().toString(),
                        mediaFileName = dest.name,
                        localFilePath = dest.absolutePath,
                        caption = "Figure: Embedded Image"
                    )
                    val afterId = _uiState.value.selectedDocxBlockId
                    mutateDocx(selectBlockId = img.id, banner = "Inserted image into document") { m ->
                        DocxDocumentMutator.insertBlockAfter(m, afterId, img)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusBanner = "Image insert failed: ${err.message}") }
                }
        }
    }

    fun resizeDocxImage(blockId: String, scaleFactor: Float) {
        mutateDocx { model ->
            model.copy(
                blocks = model.blocks.map { block ->
                    if (block is DocxBlock.ImageBlock && block.id == blockId) {
                        block.copy(
                            widthDp = (block.widthDp * scaleFactor).coerceIn(140f, 640f),
                            heightDp = (block.heightDp * scaleFactor).coerceIn(90f, 480f)
                        )
                    } else block
                }
            )
        }
    }

    fun updateDocxImageCaption(blockId: String, caption: String) {
        mutateDocx { model ->
            model.copy(
                blocks = model.blocks.map { block ->
                    if (block is DocxBlock.ImageBlock && block.id == blockId) block.copy(caption = caption)
                    else block
                }
            )
        }
    }

    fun insertDocxDrawing(title: String, strokes: List<List<NormalizedPoint>>, colorHex: String) {
        if (strokes.isEmpty()) return
        val drawing = DocxDocumentMutator.createDrawingBlock(title, strokes, colorHex)
        val afterId = _uiState.value.selectedDocxBlockId
        mutateDocx(selectBlockId = drawing.id, banner = "Inserted stylus drawing canvas") { model ->
            DocxDocumentMutator.insertBlockAfter(model, afterId, drawing)
        }
        openWordDialog(null)
    }

    fun applyDocxHyperlink(url: String, linkText: String?) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val targetId = _uiState.value.selectedDocxBlockId ?: return
        mutateDocx(banner = "Attached hyperlink $cleanUrl") { model ->
            val exists = model.blocks.any { it is DocxBlock.Paragraph && it.id == targetId }
            if (!exists) return@mutateDocx model
            DocxDocumentMutator.updateSelectedParagraph(model, targetId) { para ->
                val runs = if (!linkText.isNullOrBlank()) {
                    para.runs + DocxTextRun(
                        id = UUID.randomUUID().toString(),
                        text = " ${linkText.trim()}",
                        underline = true,
                        colorHex = "0F548C",
                        hyperlinkUrl = cleanUrl
                    )
                } else {
                    para.runs.map { it.copy(underline = true, colorHex = "0F548C", hyperlinkUrl = cleanUrl) }
                }
                para.copy(runs = runs)
            }
        }
        openWordDialog(null)
    }

    fun addDocxComment(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val targetId = _uiState.value.selectedDocxBlockId
            ?: _uiState.value.activeDocxModel?.blocks?.filterIsInstance<DocxBlock.Paragraph>()?.firstOrNull()?.id
            ?: return
        mutateDocx(banner = "Added review comment") { model ->
            DocxDocumentMutator.addComment(model, targetId, clean)
        }
        _uiState.update { it.copy(activeWordDialog = null, showWordReviewPanel = true) }
    }

    fun toggleDocxCommentResolved(commentId: String) {
        mutateDocx { model ->
            model.copy(
                comments = model.comments.map {
                    if (it.id == commentId) it.copy(resolved = !it.resolved) else it
                }
            )
        }
    }

    fun deleteDocxComment(commentId: String) {
        mutateDocx { model ->
            model.copy(
                comments = model.comments.filterNot { it.id == commentId },
                blocks = model.blocks.map { block ->
                    if (block is DocxBlock.Paragraph) {
                        block.copy(commentIds = block.commentIds.filterNot { it == commentId })
                    } else block
                }
            )
        }
    }

    fun updateDocxHeaderFooter(headerText: String, footerText: String, showPageNumbers: Boolean) {
        mutateDocx(banner = "Updated Header & Footer") { model ->
            model.copy(
                headerText = headerText.trim(),
                footerText = footerText.trim(),
                showPageNumbers = showPageNumbers
            )
        }
        openWordDialog(null)
    }

    fun toggleDocxPageNumbers() {
        mutateDocx { model -> model.copy(showPageNumbers = !model.showPageNumbers) }
    }

    fun insertDocxTextBox(text: String, borderColorHex: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val box = DocxBlock.TextBoxBlock(
            id = UUID.randomUUID().toString(),
            text = clean,
            borderColorHex = borderColorHex
        )
        val afterId = _uiState.value.selectedDocxBlockId
        mutateDocx(selectBlockId = box.id, banner = "Inserted Text Box") { model ->
            DocxDocumentMutator.insertBlockAfter(model, afterId, box)
        }
        openWordDialog(null)
    }

    fun insertDocxEquation(expression: String) {
        val clean = expression.trim()
        if (clean.isEmpty()) return
        val eq = DocxBlock.EquationBlock(
            id = UUID.randomUUID().toString(),
            expression = clean,
            displayUnicode = clean
        )
        val afterId = _uiState.value.selectedDocxBlockId
        mutateDocx(selectBlockId = eq.id, banner = "Inserted Equation") { model ->
            DocxDocumentMutator.insertBlockAfter(model, afterId, eq)
        }
        openWordDialog(null)
    }

    fun deleteDocxBlock(blockId: String) {
        mutateDocx(banner = "Deleted block") { model ->
            val next = model.blocks.filterNot { it.id == blockId }
            model.copy(blocks = next)
        }
    }

    // Layout Tab
    fun setDocxMarginsPreset(preset: DocxMarginsPreset) {
        mutateDocx(banner = "Margins: ${preset.label}") { model ->
            model.copy(margins = DocxDocumentMutator.applyMarginsPreset(preset))
        }
    }

    fun setDocxCustomMargins(top: Float, bottom: Float, left: Float, right: Float) {
        mutateDocx(banner = "Updated custom page margins") { model ->
            model.copy(
                margins = DocxMargins(
                    preset = DocxMarginsPreset.CUSTOM,
                    topInches = top.coerceIn(0.25f, 2.5f),
                    bottomInches = bottom.coerceIn(0.25f, 2.5f),
                    leftInches = left.coerceIn(0.25f, 2.5f),
                    rightInches = right.coerceIn(0.25f, 2.5f)
                )
            )
        }
        openWordDialog(null)
    }

    fun setDocxOrientation(orientation: DocxOrientation) {
        mutateDocx(banner = "Orientation: ${orientation.label}") { model ->
            model.copy(orientation = orientation)
        }
    }

    fun setDocxPaperSize(paperSize: DocxPaperSize) {
        mutateDocx(banner = "Paper Size: ${paperSize.label}") { model ->
            model.copy(paperSize = paperSize)
        }
    }

    fun setDocxColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 3)
        mutateDocx(banner = "Columns: $clamped") { model ->
            model.copy(columnCount = clamped)
        }
    }

    // References Tab
    fun insertOrRefreshTableOfContents() {
        mutateDocx(banner = "Updated Table of Contents from Headings") { model ->
            val existing = model.blocks.firstOrNull { it is DocxBlock.TableOfContentsBlock }
            if (existing != null) {
                model
            } else {
                val toc = DocxBlock.TableOfContentsBlock(id = UUID.randomUUID().toString())
                model.copy(blocks = listOf(toc) + model.blocks)
            }
        }
    }

    fun insertDocxNote(kind: DocxNoteKind, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val targetId = _uiState.value.selectedDocxBlockId
            ?: _uiState.value.activeDocxModel?.blocks?.filterIsInstance<DocxBlock.Paragraph>()?.firstOrNull()?.id
            ?: return
        mutateDocx(banner = "Inserted ${kind.name.lowercase()}") { model ->
            DocxDocumentMutator.insertNote(model, targetId, kind, clean)
        }
        openWordDialog(null)
    }

    fun addDocxCitation(tag: String, author: String, title: String, year: String, publisher: String, style: String) {
        if (author.isBlank() || title.isBlank()) return
        val cit = DocxCitation(
            id = UUID.randomUUID().toString(),
            tag = tag.ifBlank { "${author.take(4)}$year" },
            author = author.trim(),
            title = title.trim(),
            year = year.ifBlank { "2025" },
            publisher = publisher.trim(),
            style = style
        )
        mutateDocx(banner = "Added citation [${cit.tag}]") { model ->
            model.copy(citations = model.citations + cit)
        }
        openWordDialog(null)
    }

    fun insertBibliographyBlock() {
        val model = _uiState.value.activeDocxModel ?: return
        if (model.citations.isEmpty()) {
            _uiState.update { it.copy(statusBanner = "Add at least one citation before inserting Bibliography") }
            openWordDialog(WordDialogRequest.CITATION)
            return
        }
        val heading = DocxDocumentMutator.createParagraph(DocxParagraphStyle.HEADING_2).let {
            it.copy(runs = listOf(it.runs.first().copy(text = "References & Bibliography")))
        }
        val entryBlocks = model.citations.map { cit ->
            DocxBlock.Paragraph(
                id = UUID.randomUUID().toString(),
                style = DocxParagraphStyle.NORMAL,
                leftIndentPt = 18f,
                runs = listOf(DocxTextRun(UUID.randomUUID().toString(), DocxDocumentMutator.formatCitationEntry(cit)))
            )
        }
        mutateDocx(banner = "Inserted Bibliography (${entryBlocks.size} entries)") { m ->
            m.copy(blocks = m.blocks + heading + entryBlocks)
        }
    }

    // Review Tab
    fun toggleDocxTrackChanges() {
        mutateDocx { model ->
            val next = !model.trackChangesEnabled
            model.copy(trackChangesEnabled = next)
        }
        val enabled = _uiState.value.activeDocxModel?.trackChangesEnabled == true
        _uiState.update { it.copy(statusBanner = if (enabled) "Track Changes ON" else "Track Changes OFF") }
    }

    fun toggleDocxShowMarkup() {
        mutateDocx { model -> model.copy(showMarkup = !model.showMarkup) }
    }

    fun acceptDocxTrackedChange(blockId: String, runId: String) {
        mutateDocx { model -> DocxDocumentMutator.acceptTrackedChange(model, blockId, runId) }
    }

    fun rejectDocxTrackedChange(blockId: String, runId: String) {
        mutateDocx { model -> DocxDocumentMutator.rejectTrackedChange(model, blockId, runId) }
    }

    fun acceptAllDocxTrackedChanges() {
        mutateDocx(banner = "Accepted all tracked changes") { DocxDocumentMutator.acceptAllTrackedChanges(it) }
    }

    fun rejectAllDocxTrackedChanges() {
        mutateDocx(banner = "Rejected all tracked changes") { DocxDocumentMutator.rejectAllTrackedChanges(it) }
    }

    fun toggleWordReviewPanel() {
        _uiState.update { it.copy(showWordReviewPanel = !it.showWordReviewPanel) }
    }

    fun toggleDocxFocusMode() {
        _uiState.update { it.copy(docxFocusMode = !it.docxFocusMode) }
    }

    // View Tab
    fun setDocxViewMode(mode: DocxViewMode) {
        _uiState.update { it.copy(docxViewMode = mode) }
    }

    fun toggleDocxRulers() {
        _uiState.update { it.copy(docxShowRulers = !it.docxShowRulers) }
    }

    fun setDocxZoom(zoom: Float) {
        _uiState.update { it.copy(docxZoomScale = zoom.coerceIn(0.5f, 4.0f)) }
    }

    fun setDocxPaperTone(tone: DocxPaperTone) {
        _uiState.update { it.copy(docxPaperTone = tone) }
    }

    // ==================== XLSX EDITING & FORMULAS ====================

    fun selectXlsxCell(row: Int, col: Int) {
        val wb = _uiState.value.activeXlsxModel ?: return
        val sheet = wb.worksheets.getOrNull(wb.activeSheetIndex) ?: return
        val cell = sheet.cells[row to col]
        _uiState.update {
            it.copy(
                selectedCell = row to col,
                formulaBarInput = cell?.rawInput ?: ""
            )
        }
    }

    fun updateFormulaBarText(text: String) {
        _uiState.update { it.copy(formulaBarInput = text) }
    }

    fun commitFormulaBarEdit() {
        val wb = _uiState.value.activeXlsxModel ?: return
        val sheetIdx = wb.activeSheetIndex
        val sheet = wb.worksheets.getOrNull(sheetIdx) ?: return
        val coord = _uiState.value.selectedCell
        val raw = _uiState.value.formulaBarInput.trim()

        val existing = sheet.cells[coord]
        val isFormula = raw.startsWith("=")
        val updatedCell = XlsxCell(
            row = coord.first,
            col = coord.second,
            rawInput = raw,
            formula = if (isFormula) raw.removePrefix("=") else null,
            cachedValue = if (isFormula) "" else raw,
            displayValue = raw,
            numberFormat = existing?.numberFormat ?: "General",
            isBold = existing?.isBold ?: false
        )

        val nextCells = sheet.cells.toMutableMap().apply {
            if (raw.isEmpty()) remove(coord) else put(coord, updatedCell)
        }
        val recalculated = SpreadsheetFormulaEngine.recalculateWorksheet(
            sheet.copy(cells = nextCells)
        )
        val nextSheets = wb.worksheets.mapIndexed { idx, s -> if (idx == sheetIdx) recalculated else s }
        _uiState.update { it.copy(activeXlsxModel = wb.copy(worksheets = nextSheets)) }
        markTabDirty(wb.documentId, true)
        scheduleDebouncedAutosave(wb.documentId, "XLSX cell edit autosave")
    }

    fun toggleSelectedCellBold() {
        val wb = _uiState.value.activeXlsxModel ?: return
        val idx = wb.activeSheetIndex
        val sheet = wb.worksheets.getOrNull(idx) ?: return
        val coord = _uiState.value.selectedCell
        val cell = sheet.cells[coord] ?: XlsxCell(coord.first, coord.second)
        val updated = cell.copy(isBold = !cell.isBold)
        val nextSheet = sheet.copy(cells = sheet.cells + (coord to updated))
        _uiState.update {
            it.copy(
                activeXlsxModel = wb.copy(
                    worksheets = wb.worksheets.mapIndexed { i, s -> if (i == idx) nextSheet else s }
                )
            )
        }
        markTabDirty(wb.documentId, true)
    }

    fun toggleSelectedCellCurrency() {
        val wb = _uiState.value.activeXlsxModel ?: return
        val idx = wb.activeSheetIndex
        val sheet = wb.worksheets.getOrNull(idx) ?: return
        val coord = _uiState.value.selectedCell
        val cell = sheet.cells[coord] ?: return
        val nextFmt = if (cell.numberFormat.contains("RM")) "General" else "RM #,##0.00"
        val updated = cell.copy(
            numberFormat = nextFmt,
            displayValue = SpreadsheetFormulaEngine.formatDisplayValue(cell.cachedValue, nextFmt)
        )
        val nextSheet = sheet.copy(cells = sheet.cells + (coord to updated))
        _uiState.update {
            it.copy(
                activeXlsxModel = wb.copy(
                    worksheets = wb.worksheets.mapIndexed { i, s -> if (i == idx) nextSheet else s }
                )
            )
        }
        markTabDirty(wb.documentId, true)
    }

    fun selectXlsxSheet(index: Int) {
        val wb = _uiState.value.activeXlsxModel ?: return
        if (index !in wb.worksheets.indices) return
        val cell = wb.worksheets[index].cells[0 to 0]
        _uiState.update {
            it.copy(
                activeXlsxModel = wb.copy(activeSheetIndex = index),
                selectedCell = 0 to 0,
                formulaBarInput = cell?.rawInput ?: ""
            )
        }
    }

    fun addXlsxWorksheet() {
        val wb = _uiState.value.activeXlsxModel ?: return
        val nextId = wb.worksheets.size + 1
        val newSheet = XlsxWorksheet(sheetId = nextId, name = "Sheet$nextId")
        _uiState.update {
            it.copy(
                activeXlsxModel = wb.copy(
                    worksheets = wb.worksheets + newSheet,
                    activeSheetIndex = wb.worksheets.size
                ),
                selectedCell = 0 to 0,
                formulaBarInput = ""
            )
        }
        markTabDirty(wb.documentId, true)
    }

    fun saveActiveDocument() {
        val tab = _uiState.value.activeTab ?: return
        viewModelScope.launch {
            when (tab.format) {
                DocumentFormat.PDF -> flattenAndSaveActivePdf(exportUri = null)
                DocumentFormat.DOCX -> {
                    val model = _uiState.value.activeDocxModel ?: return@launch
                    repository.saveDocx(model)
                        .onSuccess {
                            markTabDirty(tab.documentId, false)
                            _uiState.update { it.copy(statusBanner = "Saved ${tab.title}.docx") }
                        }
                        .onFailure { err ->
                            _uiState.update { it.copy(statusBanner = "Save failed: ${err.message}") }
                        }
                }
                DocumentFormat.XLSX -> {
                    val model = _uiState.value.activeXlsxModel ?: return@launch
                    repository.saveXlsx(model)
                        .onSuccess {
                            markTabDirty(tab.documentId, false)
                            _uiState.update { it.copy(statusBanner = "Saved ${tab.title}.xlsx") }
                        }
                        .onFailure { err ->
                            _uiState.update { it.copy(statusBanner = "Save failed: ${err.message}") }
                        }
                }
            }
        }
    }

    fun handleShortcutCommand(
        cmd: ShortcutCommand,
        onOpenFilePicker: () -> Unit,
        onExportPdf: () -> Unit,
        onPrintDocument: () -> Unit
    ) {
        val tab = _uiState.value.activeTab
        when (cmd) {
            ShortcutCommand.SAVE -> saveActiveDocument()
            ShortcutCommand.EXPORT_FLATTEN -> if (tab?.format == DocumentFormat.PDF) onExportPdf() else saveActiveDocument()
            ShortcutCommand.PRINT -> onPrintDocument()
            ShortcutCommand.OPEN_FILE -> onOpenFilePicker()
            ShortcutCommand.NEW_DOCX -> setShowNewDocDialog(true)
            ShortcutCommand.CLOSE_TAB -> tab?.let { closeTab(it.documentId) }
            ShortcutCommand.NEXT_TAB -> {
                val tabs = _uiState.value.openTabs
                if (tabs.size > 1 && tab != null) {
                    val nextIdx = (tabs.indexOfFirst { it.documentId == tab.documentId } + 1) % tabs.size
                    switchTab(tabs[nextIdx].documentId)
                }
            }
            ShortcutCommand.FIND -> toggleSearchBar()
            ShortcutCommand.FIND_REPLACE -> {
                if (tab?.format == DocumentFormat.DOCX) openDocxFindReplace(replaceMode = true)
                else toggleSearchBar()
            }
            ShortcutCommand.UNDO -> undo()
            ShortcutCommand.REDO -> redo()
            ShortcutCommand.FORMAT_BOLD -> {
                if (tab?.format == DocumentFormat.DOCX) toggleDocxFormatting(bold = true)
                else if (tab?.format == DocumentFormat.XLSX) toggleSelectedCellBold()
            }
            ShortcutCommand.FORMAT_ITALIC -> if (tab?.format == DocumentFormat.DOCX) toggleDocxFormatting(italic = true)
            ShortcutCommand.FORMAT_UNDERLINE -> if (tab?.format == DocumentFormat.DOCX) toggleDocxFormatting(underline = true)
            ShortcutCommand.INSERT_PAGE_BREAK -> if (tab?.format == DocumentFormat.DOCX) insertDocxPageBreak()
            ShortcutCommand.ZOOM_IN -> {
                if (tab?.format == DocumentFormat.DOCX) setDocxZoom(_uiState.value.docxZoomScale + 0.25f)
                else tab?.let { setPdfZoom(it.zoomScale + 0.25f) }
            }
            ShortcutCommand.ZOOM_OUT -> {
                if (tab?.format == DocumentFormat.DOCX) setDocxZoom(_uiState.value.docxZoomScale - 0.25f)
                else tab?.let { setPdfZoom(it.zoomScale - 0.25f) }
            }
            ShortcutCommand.ZOOM_RESET -> {
                if (tab?.format == DocumentFormat.DOCX) setDocxZoom(1.0f)
                else setPdfZoom(1.0f)
            }
            ShortcutCommand.TOGGLE_RIBBON -> toggleRibbonExpanded()
            ShortcutCommand.TOOL_BROWSE -> selectPdfTool(PdfEditorTool.BROWSE)
            ShortcutCommand.TOOL_SELECT -> selectPdfTool(PdfEditorTool.SELECT_TEXT)
            ShortcutCommand.TOOL_HIGHLIGHT -> selectPdfTool(PdfEditorTool.HIGHLIGHT)
            ShortcutCommand.TOOL_INK -> selectPdfTool(PdfEditorTool.INK_PEN)
            ShortcutCommand.ESCAPE -> {
                _uiState.update {
                    it.copy(
                        isSearchVisible = false,
                        showDocxFindReplaceBar = false,
                        showShortcutsModal = false,
                        showNewDocDialog = false,
                        showSignaturePad = false,
                        activeWordDialog = null,
                        docxFocusMode = false,
                        activePdfTool = PdfEditorTool.BROWSE,
                        pendingSignature = null
                    )
                }
            }
            ShortcutCommand.SHOW_SHORTCUTS_HELP -> setShowShortcutsModal(true)
        }
    }

    private fun scheduleDebouncedAutosave(documentId: String, reason: String) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(2500L)
            repository.createAutosaveSnapshot(documentId, reason)
        }
    }

    private fun updateActiveTab(transform: (DocumentTab) -> DocumentTab) {
        val activeId = _uiState.value.activeTabId ?: return
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { tab ->
                    if (tab.documentId == activeId) transform(tab) else tab
                }
            )
        }
    }

    private fun markTabDirty(documentId: String, dirty: Boolean) {
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { tab ->
                    if (tab.documentId == documentId) tab.copy(isDirty = dirty) else tab
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { repository.pdfRenderEngine.close() }
    }
}
