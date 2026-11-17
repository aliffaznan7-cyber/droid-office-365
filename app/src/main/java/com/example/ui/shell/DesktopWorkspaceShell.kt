package com.example.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.shortcuts.KeyboardShortcutRegistry
import com.example.data.local.AutosaveEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfMetadataEntity
import com.example.domain.model.DocumentFormat
import com.example.domain.model.PdfAnnotation
import com.example.domain.model.SignaturePreset
import com.example.ui.docx.DocxEditorScreen
import com.example.ui.docx.WordReviewPanel
import com.example.ui.pdf.PdfReaderEditorScreen
import com.example.ui.pdf.SignaturePadDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.DocxDocumentMutator
import com.example.ui.viewmodel.WorkspaceUiState
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.ui.xlsx.XlsxSpreadsheetScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DesktopWorkspaceShell(
    uiState: WorkspaceUiState,
    libraryDocuments: List<DocumentEntity>,
    annotations: List<PdfAnnotation>,
    pdfMetadata: PdfMetadataEntity?,
    autosaves: List<AutosaveEntity>,
    savedSignatures: List<SignaturePreset>,
    viewModel: WorkspaceViewModel,
    onOpenFilePicker: () -> Unit,
    onExportFlattenPdf: () -> Unit,
    onPrintDocument: () -> Unit,
    onInsertPicture: () -> Unit
) {
    val pdfListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeTab = uiState.activeTab
    val activeDocEntity = remember(libraryDocuments, activeTab?.documentId) {
        libraryDocuments.firstOrNull { it.id == activeTab?.documentId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onPreviewKeyEvent { keyEvent ->
                val cmd = KeyboardShortcutRegistry.matchCommand(keyEvent)
                if (cmd != null) {
                    viewModel.handleShortcutCommand(
                        cmd = cmd,
                        onOpenFilePicker = onOpenFilePicker,
                        onExportPdf = onExportFlattenPdf,
                        onPrintDocument = onPrintDocument
                    )
                    true
                } else {
                    false
                }
            }
    ) {
        val inFocusMode = uiState.docxFocusMode && activeTab?.format == DocumentFormat.DOCX

        // Multi-Tab Switcher Bar
        AnimatedVisibility(visible = !inFocusMode) {
            MultiTabBar(
                tabs = uiState.openTabs,
                activeTabId = uiState.activeTabId,
                onSelectTab = viewModel::switchTab,
                onCloseTab = viewModel::closeTab,
                onNewTab = { viewModel.setShowNewDocDialog(true) }
            )
        }

        // Collapsible Fluent Desktop Ribbon
        AnimatedVisibility(visible = !inFocusMode) {
            DesktopRibbonBar(
                uiState = uiState,
                viewModel = viewModel,
                onSelectRibbonTab = viewModel::selectRibbonTab,
                onToggleRibbonExpanded = viewModel::toggleRibbonExpanded,
                onOpenFile = onOpenFilePicker,
                onNewDoc = { viewModel.setShowNewDocDialog(true) },
                onSave = viewModel::saveActiveDocument,
                onExportFlattenPdf = onExportFlattenPdf,
                onPrintDocument = onPrintDocument,
                onInsertPicture = onInsertPicture,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onFind = viewModel::toggleSearchBar,
                onToggleNavPane = viewModel::toggleNavPane,
                onToggleInspector = viewModel::toggleInspectorPane,
                onToggleDarkTheme = viewModel::toggleDarkCanvas,
                onShowShortcuts = { viewModel.setShowShortcutsModal(true) },
                onSelectPdfTool = viewModel::selectPdfTool,
                onSelectInkColor = viewModel::setInkColor,
                onSelectStrokeWidth = viewModel::setStrokeWidth,
                onOpenSignaturePad = { viewModel.setShowSignaturePad(true) },
                onZoomDelta = { delta ->
                    activeTab?.let { viewModel.setPdfZoom(it.zoomScale + delta) }
                },
                onXlsxBold = viewModel::toggleSelectedCellBold,
                onXlsxCurrency = viewModel::toggleSelectedCellCurrency,
                onXlsxAddSheet = viewModel::addXlsxWorksheet
            )
        }

        // Responsive Multi-Pane Desktop Content Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWideTablet = maxWidth >= 820.dp
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = !inFocusMode && uiState.isNavPaneOpen && (isWideTablet || activeTab == null)) {
                    DocumentNavigationPane(
                        documents = libraryDocuments,
                        activeDocumentId = activeTab?.documentId,
                        activeFormat = activeTab?.format,
                        activeFilePath = activeTab?.filePath,
                        pdfDimensions = uiState.pdfDimensions,
                        currentPdfPage = activeTab?.currentPage ?: 0,
                        renderEngine = viewModel.repository.pdfRenderEngine,
                        onOpenDocument = viewModel::openDocument,
                        onDeleteDocument = viewModel::deleteDocument,
                        onTogglePin = viewModel::toggleOfflinePin,
                        onJumpToPdfPage = { pageIdx ->
                            scope.launch { pdfListState.animateScrollToItem(pageIdx) }
                        },
                        onImportFile = onOpenFilePicker,
                        onCreateNew = { viewModel.setShowNewDocDialog(true) }
                    )
                }

                // Active Document Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    when (activeTab?.format) {
                        DocumentFormat.PDF -> {
                            PdfReaderEditorScreen(
                                uiState = uiState,
                                annotations = annotations,
                                renderEngine = viewModel.repository.pdfRenderEngine,
                                listState = pdfListState,
                                onPageChanged = viewModel::setPdfCurrentPage,
                                onZoomChanged = viewModel::setPdfZoom,
                                onAddInkStroke = viewModel::addPdfInkStroke,
                                onAddHighlight = viewModel::addPdfHighlight,
                                onAddTextBox = viewModel::addPdfTextBox,
                                onStampSignature = viewModel::stampPendingSignature,
                                onEraseNear = viewModel::eraseAnnotationNear,
                                onSelectText = viewModel::setSelectedPdfText,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onCloseSearch = viewModel::toggleSearchBar
                            )
                        }

                        DocumentFormat.DOCX -> {
                            DocxEditorScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }

                        DocumentFormat.XLSX -> {
                            XlsxSpreadsheetScreen(
                                model = uiState.activeXlsxModel,
                                selectedCell = uiState.selectedCell,
                                formulaBarInput = uiState.formulaBarInput,
                                onSelectCell = viewModel::selectXlsxCell,
                                onFormulaInputChange = viewModel::updateFormulaBarText,
                                onCommitFormula = viewModel::commitFormulaBarEdit,
                                onSelectSheet = viewModel::selectXlsxSheet,
                                onAddSheet = viewModel::addXlsxWorksheet
                            )
                        }

                        null -> {
                            EmptyWorkspaceView(
                                onOpenFile = onOpenFilePicker,
                                onNewDoc = { viewModel.setShowNewDocDialog(true) }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !inFocusMode && uiState.showWordReviewPanel && activeTab?.format == DocumentFormat.DOCX && isWideTablet) {
                    WordReviewPanel(
                        model = uiState.activeDocxModel,
                        onSelectBlock = viewModel::selectDocxBlock,
                        onToggleCommentResolved = viewModel::toggleDocxCommentResolved,
                        onDeleteComment = viewModel::deleteDocxComment,
                        onAcceptChange = viewModel::acceptDocxTrackedChange,
                        onRejectChange = viewModel::rejectDocxTrackedChange,
                        onAcceptAllChanges = viewModel::acceptAllDocxTrackedChanges,
                        onRejectAllChanges = viewModel::rejectAllDocxTrackedChanges,
                        onOpenDialog = { viewModel.openWordDialog(it) },
                        onClosePanel = viewModel::toggleWordReviewPanel
                    )
                }

                AnimatedVisibility(visible = !inFocusMode && uiState.isInspectorOpen && isWideTablet) {
                    TaskInspectorPane(
                        activeDoc = activeDocEntity,
                        pdfMetadata = pdfMetadata,
                        annotations = annotations,
                        autosaves = autosaves,
                        onDeleteAnnotation = { annId ->
                            activeTab?.documentId?.let { docId ->
                                scope.launch { viewModel.repository.deleteAnnotation(docId, annId) }
                            }
                        },
                        onJumpToPage = { pageIdx ->
                            scope.launch { pdfListState.animateScrollToItem(pageIdx) }
                        }
                    )
                }
            }
        }

        // Bottom Desktop Status Bar
        val detailText = when (activeTab?.format) {
            DocumentFormat.PDF -> {
                val total = uiState.pdfDimensions.size
                "Page ${activeTab.currentPage + 1}/$total  •  Zoom ${(activeTab.zoomScale * 100).roundToInt()}%  •  ${annotations.count { !it.isFlattened }} pending vector edits"
            }
            DocumentFormat.DOCX -> {
                val (words, chars) = DocxDocumentMutator.computeWordAndCharCounts(uiState.activeDocxModel)
                val track = if (uiState.activeDocxModel?.trackChangesEnabled == true) "Track ON" else "Track OFF"
                "$words words  •  $chars chars  •  $track  •  ${uiState.docxViewMode.label}  •  Zoom ${(uiState.docxZoomScale * 100).roundToInt()}%"
            }
            DocumentFormat.XLSX -> {
                val sheets = uiState.activeXlsxModel?.worksheets?.size ?: 0
                "SpreadsheetML  •  $sheets worksheets"
            }
            null -> "${libraryDocuments.size} local files ready"
        }

        Box(modifier = Modifier.navigationBarsPadding()) {
            DesktopStatusBar(
                activeTabTitle = activeTab?.title,
                format = activeTab?.format,
                detailText = detailText,
                statusBanner = uiState.statusBanner,
                onShowShortcuts = { viewModel.setShowShortcutsModal(true) },
                onDismissBanner = viewModel::clearStatusBanner
            )
        }
    }

    // Vector Signature Pad Dialog
    if (uiState.showSignaturePad) {
        SignaturePadDialog(
            savedSignatures = savedSignatures,
            activeColorArgb = uiState.activeInkColorArgb,
            onSelectPreset = { preset ->
                viewModel.setShowSignaturePad(false)
                viewModel.armSignaturePlacement(preset)
            },
            onDeletePreset = viewModel::deleteSignaturePreset,
            onSaveNewSignature = viewModel::saveSignaturePreset,
            onDismiss = { viewModel.setShowSignaturePad(false) }
        )
    }

    // New Document Creation Modal
    if (uiState.showNewDocDialog) {
        NewDocumentDialog(
            onCreate = viewModel::createNewDocument,
            onDismiss = { viewModel.setShowNewDocDialog(false) }
        )
    }

    // Keyboard Shortcuts Reference Modal
    if (uiState.showShortcutsModal) {
        KeyboardShortcutsModal(
            onDismiss = { viewModel.setShowShortcutsModal(false) }
        )
    }
}

@Composable
private fun EmptyWorkspaceView(
    onOpenFile: () -> Unit,
    onNewDoc: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.empty_workspace_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_workspace_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenFile) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import PDF / Office File")
                }
                Button(onClick = onNewDoc) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Document")
                }
            }
        }
    }
}

@Composable
private fun NewDocumentDialog(
    onCreate: (DocumentFormat, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("Untitled Document") }
    var selectedFormat by remember { mutableStateOf(DocumentFormat.DOCX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Local Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_doc_title_input")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        Triple(DocumentFormat.DOCX, "Word (.docx)", Icons.Default.Description),
                        Triple(DocumentFormat.XLSX, "Excel (.xlsx)", Icons.Default.TableChart),
                        Triple(DocumentFormat.PDF, "Vector (.pdf)", Icons.Default.PictureAsPdf)
                    ).forEach { (fmt, label, icon) ->
                        val active = selectedFormat == fmt
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedFormat = fmt }
                                .testTag("new_doc_format_${fmt.extension}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                            ) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(selectedFormat, title.ifBlank { "Untitled" }) },
                modifier = Modifier.testTag("confirm_create_doc")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun KeyboardShortcutsModal(onDismiss: () -> Unit) {
    val shortcuts = listOf(
        "Ctrl + S" to "Save active document (or flatten PDF)",
        "Ctrl + P" to "Print active Word or PDF document",
        "Ctrl + Shift + S" to "Flatten & Export lossless vector PDF",
        "Ctrl + B / I / U" to "Bold / Italic / Underline in Word",
        "Ctrl + Enter" to "Insert Page Break in Word",
        "Ctrl + F / H" to "Find / Find & Replace in document",
        "Ctrl + Z / Y" to "Undo / Redo Word edits & PDF annotations",
        "Ctrl + O / N" to "Import / Create new document",
        "Ctrl + W / Tab" to "Close active tab / Switch next tab",
        "Ctrl + + / - / 0" to "Zoom In / Out / Reset 100%",
        "Ctrl + 1..4" to "PDF Browse / Select / Highlight / Stylus Ink",
        "Meta + /" to "Open Android Keyboard Shortcuts Helper"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xiaomi Pad 7 Keyboard & Stylus Shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { (keys, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = keys,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}
