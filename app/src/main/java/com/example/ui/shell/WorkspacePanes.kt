package com.example.ui.shell

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.pdf.PdfPageDimension
import com.example.core.pdf.PdfRenderEngine
import com.example.data.local.AutosaveEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfMetadataEntity
import com.example.domain.model.DocumentFormat
import com.example.domain.model.DocumentTab
import com.example.domain.model.PdfAnnotation
import com.example.ui.theme.FormatDocxColor
import com.example.ui.theme.FormatPdfColor
import com.example.ui.theme.FormatXlsxColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MultiTabBar(
    tabs: List<DocumentTab>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { tab ->
                val isActive = tab.documentId == activeTabId
                val accent = formatAccentColor(tab.format)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                    modifier = Modifier
                        .border(
                            width = if (isActive) 1.dp else 0.5.dp,
                            color = if (isActive) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTab(tab.documentId) }
                        .testTag("doc_tab_${tab.documentId}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (tab.isDirty) "${tab.title} •" else tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close ${tab.title}",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { onCloseTab(tab.documentId) }
                                .padding(4.dp)
                                .testTag("close_tab_${tab.documentId}")
                        )
                    }
                }
            }
            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("tab_bar_new_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New document", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun DocumentNavigationPane(
    documents: List<DocumentEntity>,
    activeDocumentId: String?,
    activeFormat: DocumentFormat?,
    activeFilePath: String?,
    pdfDimensions: List<PdfPageDimension>,
    currentPdfPage: Int,
    renderEngine: PdfRenderEngine,
    onOpenDocument: (DocumentEntity) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onJumpToPdfPage: (Int) -> Unit,
    onImportFile: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember(activeFormat) {
        mutableIntStateOf(0)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .width(268.dp)
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (activeFormat == DocumentFormat.PDF && pdfDimensions.isNotEmpty()) {
                TabRow(selectedTabIndex = selectedMode) {
                    Tab(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        text = { Text("Library") }
                    )
                    Tab(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        text = { Text("Pages (${pdfDimensions.size})") }
                    )
                }
            } else {
                Text(
                    text = "Local Workspace",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (selectedMode == 1 && activeFormat == DocumentFormat.PDF && activeFilePath != null && activeDocumentId != null) {
                val pdfFile = remember(activeFilePath) { File(activeFilePath) }
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pdf_thumbnails_list")
                ) {
                    items(pdfDimensions, key = { it.pageIndex }) { dim ->
                        PdfThumbnailCard(
                            documentId = activeDocumentId,
                            pdfFile = pdfFile,
                            dimension = dim,
                            isCurrent = dim.pageIndex == currentPdfPage,
                            renderEngine = renderEngine,
                            onClick = { onJumpToPdfPage(dim.pageIndex) }
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportFile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_import_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onCreateNew,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_new_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", maxLines = 1)
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("workspace_library_list")
                ) {
                    items(documents, key = { it.id }) { doc ->
                        val isActive = doc.id == activeDocumentId
                        val accent = formatAccentColor(doc.format)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isActive) 1.5.dp else 0.75.dp,
                                    color = if (isActive) accent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onOpenDocument(doc) }
                                .testTag("library_doc_${doc.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (doc.format) {
                                            DocumentFormat.PDF -> Icons.Default.PictureAsPdf
                                            DocumentFormat.DOCX -> Icons.Default.Description
                                            DocumentFormat.XLSX -> Icons.Default.TableChart
                                        },
                                        contentDescription = doc.format.label,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.format.label} • ${doc.pageOrSheetCount} ${if (doc.format == DocumentFormat.XLSX) "sheets" else "pg"} • ${formatBytes(doc.fileSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { onTogglePin(doc.id, doc.isPinnedOffline) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pin offline",
                                        tint = if (doc.isPinnedOffline) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDocument(doc.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete document",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnailCard(
    documentId: String,
    pdfFile: File,
    dimension: PdfPageDimension,
    isCurrent: Boolean,
    renderEngine: PdfRenderEngine,
    onClick: () -> Unit
) {
    var thumb by remember(documentId, dimension.pageIndex) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(documentId, dimension.pageIndex) {
        thumb = renderEngine.renderPage(
            file = pdfFile,
            documentKey = documentId,
            pageIndex = dimension.pageIndex,
            targetWidthPx = 200,
            isThumbnail = true
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("pdf_thumb_${dimension.pageIndex}")
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(dimension.aspectRatio)
                .background(Color.White)
                .border(0.5.dp, Color(0xFFCBD5E1))
        ) {
            thumb?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Thumbnail page ${dimension.pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Page ${dimension.pageIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun TaskInspectorPane(
    activeDoc: DocumentEntity?,
    pdfMetadata: PdfMetadataEntity?,
    annotations: List<PdfAnnotation>,
    autosaves: List<AutosaveEntity>,
    onDeleteAnnotation: (String) -> Unit,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Document Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (activeDoc != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = "Integrity verified",
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Metadata & Integrity",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            InspectorRow("Format", activeDoc.format.label)
                            InspectorRow("Department", activeDoc.department)
                            InspectorRow("Classification", activeDoc.classification)
                            InspectorRow("Size", formatBytes(activeDoc.fileSizeBytes))
                            if (pdfMetadata != null) {
                                InspectorRow("PDF Version", pdfMetadata.pdfVersion)
                                InspectorRow("Pages", "${pdfMetadata.pageCount}")
                            }
                            if (activeDoc.checksumSha256.isNotEmpty()) {
                                Text(
                                    text = "SHA-256: ${activeDoc.checksumSha256.take(16)}...",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Unflattened Annotations (${annotations.count { !it.isFlattened }})",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            val unflattened = annotations.filter { !it.isFlattened }
            if (unflattened.isEmpty()) {
                item {
                    Text(
                        text = "No pending annotations. Use Stylus Ink, Highlight, Text Box, or Signature on a PDF page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(unflattened, key = { it.id }) { ann ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpToPage(ann.pageIndex) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ann.type.name} • Page ${ann.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (ann.textContent.isNotBlank()) {
                                    Text(
                                        text = ann.textContent,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteAnnotation(ann.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove annotation")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Local Recovery Snapshots (${autosaves.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            items(autosaves, key = { it.id }) { snap ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = snap.summary, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${formatTime(snap.createdAt)} • ${formatBytes(snap.byteSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DesktopStatusBar(
    activeTabTitle: String?,
    format: DocumentFormat?,
    detailText: String,
    statusBanner: String?,
    onShowShortcuts: () -> Unit,
    onDismissBanner: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.75.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(format?.let { formatAccentColor(it) } ?: StatusSuccess)
                )
                Text(
                    text = statusBanner ?: "${activeTabTitle ?: "DocuDesk Workspace"}  •  $detailText",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { if (statusBanner != null) onDismissBanner() }
                )
            }
            TextButton(
                onClick = onShowShortcuts,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "144Hz Tablet Mode  •  Ctrl+S Save  •  Meta+/ Shortcuts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatAccentColor(format: DocumentFormat): Color = when (format) {
    DocumentFormat.PDF -> FormatPdfColor
    DocumentFormat.DOCX -> FormatDocxColor
    DocumentFormat.XLSX -> FormatXlsxColor
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}

private fun formatTime(epochMs: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}
