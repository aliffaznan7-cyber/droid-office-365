package com.example

import android.os.Bundle
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.print.DocumentPrintHelper
import com.example.core.shortcuts.KeyboardShortcutRegistry
import com.example.domain.model.DocumentFormat
import com.example.ui.shell.DesktopWorkspaceShell
import com.example.ui.theme.DocuDeskTheme
import com.example.ui.viewmodel.WorkspaceViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: WorkspaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val libraryDocuments by viewModel.libraryDocuments.collectAsStateWithLifecycle()
            val annotations by viewModel.activeAnnotations.collectAsStateWithLifecycle()
            val pdfMetadata by viewModel.activePdfMetadata.collectAsStateWithLifecycle()
            val autosaves by viewModel.activeAutosaves.collectAsStateWithLifecycle()
            val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()

            val openDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importFile(uri)
                }
            }

            val exportPdfLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/pdf")
            ) { uri ->
                if (uri != null) {
                    viewModel.flattenAndSaveActivePdf(exportUri = uri)
                }
            }

            val pickPictureLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    viewModel.insertDocxImageFromUri(uri)
                }
            }

            val triggerOpenFile = {
                openDocumentLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                )
            }

            val triggerExportPdf = {
                val baseName = uiState.activeTab?.title?.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    ?: "Annotated_Document"
                exportPdfLauncher.launch("${baseName}_flattened.pdf")
            }

            val triggerInsertPicture = {
                pickPictureLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            val triggerPrintDocument: () -> Unit = {
                val tab = uiState.activeTab
                when (tab?.format) {
                    DocumentFormat.DOCX -> {
                        uiState.activeDocxModel?.let { model ->
                            DocumentPrintHelper.printDocxModel(this@MainActivity, model)
                        }
                    }
                    DocumentFormat.PDF -> {
                        val file = File(tab.filePath)
                        if (file.exists()) {
                            DocumentPrintHelper.printPdfFile(this@MainActivity, tab.title, file)
                        }
                    }
                    else -> Unit
                }
                Unit
            }

            DocuDeskTheme(darkTheme = uiState.isDarkCanvas) {
                DesktopWorkspaceShell(
                    uiState = uiState,
                    libraryDocuments = libraryDocuments,
                    annotations = annotations,
                    pdfMetadata = pdfMetadata,
                    autosaves = autosaves,
                    savedSignatures = savedSignatures,
                    viewModel = viewModel,
                    onOpenFilePicker = triggerOpenFile,
                    onExportFlattenPdf = triggerExportPdf,
                    onPrintDocument = triggerPrintDocument,
                    onInsertPicture = triggerInsertPicture
                )
            }
        }
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int
    ) {
        data?.addAll(KeyboardShortcutRegistry.buildShortcutGroups())
    }
}
