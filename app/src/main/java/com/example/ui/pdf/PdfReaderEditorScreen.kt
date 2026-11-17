package com.example.ui.pdf

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.pdf.PdfPageDimension
import com.example.core.pdf.PdfRenderEngine
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.NormalizedRect
import com.example.domain.model.PdfAnnotation
import com.example.domain.model.PdfAnnotationType
import com.example.domain.model.PdfEditorTool
import com.example.domain.model.PdfSearchHit
import com.example.domain.model.PdfTextRun
import com.example.ui.viewmodel.WorkspaceUiState
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PdfReaderEditorScreen(
    uiState: WorkspaceUiState,
    annotations: List<PdfAnnotation>,
    renderEngine: PdfRenderEngine,
    listState: LazyListState,
    onPageChanged: (Int) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onAddInkStroke: (Int, List<NormalizedPoint>) -> Unit,
    onAddHighlight: (Int, NormalizedRect, Boolean) -> Unit,
    onAddTextBox: (Int, NormalizedRect, String) -> Unit,
    onStampSignature: (Int, Float, Float) -> Unit,
    onEraseNear: (Int, Float, Float) -> Unit,
    onSelectText: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = uiState.activeTab ?: return
    val pdfFile = remember(activeTab.filePath) { File(activeTab.filePath) }
    val dimensions = uiState.pdfDimensions
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var pendingTextBoxLocation by remember { mutableStateOf<Pair<Int, NormalizedRect>?>(null) }
    var textBoxDraft by remember { mutableStateOf("") }
    var selectedRunBounds by remember { mutableStateOf<Pair<Int, NormalizedRect>?>(null) }

    val visiblePage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(visiblePage) {
        if (dimensions.isNotEmpty()) {
            onPageChanged(visiblePage.coerceIn(0, dimensions.lastIndex))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar (Ctrl+F)
            AnimatedVisibility(visible = uiState.isSearchVisible) {
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search PDF text content...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pdf_search_input")
                            )
                            Text(
                                text = "${uiState.searchHits.size} hits",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = onCloseSearch,
                                modifier = Modifier.testTag("close_search_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        }
                        if (uiState.isSearching) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                        } else if (uiState.searchHits.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.searchHits.take(4).forEach { hit ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                scope.launch {
                                                    listState.animateScrollToItem(hit.pageIndex)
                                                }
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "Page ${hit.pageIndex + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = hit.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Text Action Strip
            AnimatedVisibility(visible = uiState.selectedPdfText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Selected: \"${uiState.selectedPdfText}\"",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(uiState.selectedPdfText))
                                },
                                modifier = Modifier.testTag("copy_selected_text_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                            val selBounds = selectedRunBounds
                            if (selBounds != null) {
                                TextButton(
                                    onClick = {
                                        onAddHighlight(selBounds.first, selBounds.second, false)
                                        onSelectText("")
                                        selectedRunBounds = null
                                    },
                                    modifier = Modifier.testTag("highlight_selected_text_button")
                                ) {
                                    Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Highlight")
                                }
                            }
                            IconButton(onClick = {
                                onSelectText("")
                                selectedRunBounds = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection")
                            }
                        }
                    }
                }
            }

            // Main 144Hz Virtualized PDF Page Stream
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val density = LocalDensity.current
                val basePageWidthDp = (maxWidth - 40.dp).coerceIn(320.dp, 860.dp)
                val zoomedPageWidthDp = basePageWidthDp * activeTab.zoomScale
                val targetWidthPx = with(density) { zoomedPageWidthDp.toPx().roundToInt().coerceIn(480, 2400) }

                if (dimensions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Initializing PDF Vector Pipeline...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 76.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pdf_lazy_column")
                    ) {
                        items(
                            items = dimensions,
                            key = { "${activeTab.documentId}_${it.pageIndex}" }
                        ) { dim ->
                            val pageAnnotations = remember(annotations, dim.pageIndex) {
                                annotations.filter { it.pageIndex == dim.pageIndex && !it.isFlattened }
                            }
                            val pageTextRuns = uiState.pageTextRuns[dim.pageIndex].orEmpty()
                            val pageSearchHits = remember(uiState.searchHits, dim.pageIndex) {
                                uiState.searchHits.filter { it.pageIndex == dim.pageIndex }
                            }

                            PdfPageViewportItem(
                                documentId = activeTab.documentId,
                                pdfFile = pdfFile,
                                dimension = dim,
                                targetWidthPx = targetWidthPx,
                                pageWidthDp = zoomedPageWidthDp,
                                activeTool = uiState.activePdfTool,
                                inkColorArgb = uiState.activeInkColorArgb,
                                highlightColorArgb = uiState.activeHighlightColorArgb,
                                strokeWidthNormalized = uiState.activeStrokeWidthNormalized,
                                annotations = pageAnnotations,
                                textRuns = pageTextRuns,
                                searchHits = pageSearchHits,
                                selectedBounds = selectedRunBounds?.takeIf { it.first == dim.pageIndex }?.second,
                                renderEngine = renderEngine,
                                onInkCompleted = { pts -> onAddInkStroke(dim.pageIndex, pts) },
                                onHighlightCompleted = { rect -> onAddHighlight(dim.pageIndex, rect, false) },
                                onTextBoxTap = { rect ->
                                    pendingTextBoxLocation = dim.pageIndex to rect
                                    textBoxDraft = ""
                                },
                                onSignatureTap = { nx, ny -> onStampSignature(dim.pageIndex, nx, ny) },
                                onEraseAt = { nx, ny -> onEraseNear(dim.pageIndex, nx, ny) },
                                onSelectRun = { run ->
                                    selectedRunBounds = dim.pageIndex to run.bounds
                                    onSelectText(run.text)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Page + Zoom HUD Pill
        if (dimensions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Page ${activeTab.currentPage + 1} / ${dimensions.size}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onZoomChanged(activeTab.zoomScale - 0.2f) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("pdf_zoom_out_button")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${(activeTab.zoomScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clickable { onZoomChanged(1.0f) }
                            .padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onZoomChanged(activeTab.zoomScale + 0.2f) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("pdf_zoom_in_button")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = uiState.activePdfTool.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Inline Text Box Creation Modal
    val textLoc = pendingTextBoxLocation
    if (textLoc != null) {
        AlertDialog(
            onDismissRequest = { pendingTextBoxLocation = null },
            title = { Text("Add PDF Vector Text Box") },
            text = {
                OutlinedTextField(
                    value = textBoxDraft,
                    onValueChange = { textBoxDraft = it },
                    label = { Text("Annotation Note") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_textbox_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTextBox(textLoc.first, textLoc.second, textBoxDraft)
                        pendingTextBoxLocation = null
                    },
                    modifier = Modifier.testTag("pdf_textbox_confirm")
                ) {
                    Text("Place Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextBoxLocation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PdfPageViewportItem(
    documentId: String,
    pdfFile: File,
    dimension: PdfPageDimension,
    targetWidthPx: Int,
    pageWidthDp: androidx.compose.ui.unit.Dp,
    activeTool: PdfEditorTool,
    inkColorArgb: Long,
    highlightColorArgb: Long,
    strokeWidthNormalized: Float,
    annotations: List<PdfAnnotation>,
    textRuns: List<PdfTextRun>,
    searchHits: List<PdfSearchHit>,
    selectedBounds: NormalizedRect?,
    renderEngine: PdfRenderEngine,
    onInkCompleted: (List<NormalizedPoint>) -> Unit,
    onHighlightCompleted: (NormalizedRect) -> Unit,
    onTextBoxTap: (NormalizedRect) -> Unit,
    onSignatureTap: (Float, Float) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    onSelectRun: (PdfTextRun) -> Unit
) {
    var bitmap by remember(documentId, dimension.pageIndex, targetWidthPx) {
        mutableStateOf<Bitmap?>(
            renderEngine.getCachedBitmap(documentId, dimension.pageIndex, targetWidthPx)
        )
    }

    LaunchedEffect(documentId, dimension.pageIndex, targetWidthPx, annotations.size) {
        val rendered = renderEngine.renderPage(
            file = pdfFile,
            documentKey = documentId,
            pageIndex = dimension.pageIndex,
            targetWidthPx = targetWidthPx,
            isThumbnail = false
        )
        if (rendered != null) bitmap = rendered
    }

    val liveInkStroke = remember { mutableStateListOf<NormalizedPoint>() }
    var liveDragRect by remember { mutableStateOf<NormalizedRect?>(null) }

    Box(
        modifier = Modifier
            .widthIn(max = pageWidthDp)
            .fillMaxWidth()
            .aspectRatio(dimension.aspectRatio)
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .pointerInput(activeTool, textRuns) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()
                    val startX = (down.position.x / w).coerceIn(0f, 1f)
                    val startY = (down.position.y / h).coerceIn(0f, 1f)
                    val isStylus = down.type == PointerType.Stylus
                    val isEraser = down.type == PointerType.Eraser

                    // Stylus always allows direct ink writing; Eraser erases; otherwise obey activeTool
                    when {
                        isEraser || activeTool == PdfEditorTool.ERASER -> {
                            down.consume()
                            onEraseAt(startX, startY)
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                onEraseAt(
                                    (ch.position.x / w).coerceIn(0f, 1f),
                                    (ch.position.y / h).coerceIn(0f, 1f)
                                )
                            }
                        }

                        isStylus || activeTool == PdfEditorTool.INK_PEN -> {
                            down.consume()
                            liveInkStroke.clear()
                            liveInkStroke.add(
                                NormalizedPoint(startX, startY, down.pressure.coerceIn(0.4f, 1.5f))
                            )
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                liveInkStroke.add(
                                    NormalizedPoint(
                                        x = (ch.position.x / w).coerceIn(0f, 1f),
                                        y = (ch.position.y / h).coerceIn(0f, 1f),
                                        pressure = ch.pressure.coerceIn(0.4f, 1.5f)
                                    )
                                )
                            }
                            if (liveInkStroke.size >= 2) {
                                onInkCompleted(liveInkStroke.toList())
                            }
                            liveInkStroke.clear()
                        }

                        activeTool == PdfEditorTool.HIGHLIGHT -> {
                            down.consume()
                            var curX = startX
                            var curY = startY
                            liveDragRect = NormalizedRect(startX, startY, startX, startY)
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                curX = (ch.position.x / w).coerceIn(0f, 1f)
                                curY = (ch.position.y / h).coerceIn(0f, 1f)
                                liveDragRect = NormalizedRect(
                                    left = minOf(startX, curX),
                                    top = minOf(startY, curY),
                                    right = maxOf(startX, curX),
                                    bottom = maxOf(startY, curY).let { b ->
                                        if (b - minOf(startY, curY) < 0.018f) minOf(startY, curY) + 0.022f else b
                                    }
                                )
                            }
                            liveDragRect?.let { rect ->
                                if (rect.right - rect.left > 0.015f) {
                                    onHighlightCompleted(rect)
                                }
                            }
                            liveDragRect = null
                        }

                        activeTool == PdfEditorTool.TEXT_BOX -> {
                            down.consume()
                            onTextBoxTap(
                                NormalizedRect(
                                    left = startX.coerceIn(0.04f, 0.72f),
                                    top = startY.coerceIn(0.04f, 0.90f),
                                    right = (startX + 0.28f).coerceAtMost(0.96f),
                                    bottom = (startY + 0.06f).coerceAtMost(0.96f)
                                )
                            )
                        }

                        activeTool == PdfEditorTool.SIGNATURE -> {
                            down.consume()
                            onSignatureTap(startX, startY)
                        }

                        activeTool == PdfEditorTool.SELECT_TEXT -> {
                            down.consume()
                            val hit = textRuns.firstOrNull { run ->
                                startX in (run.bounds.left - 0.02f)..(run.bounds.right + 0.02f) &&
                                    startY in (run.bounds.top - 0.02f)..(run.bounds.bottom + 0.02f)
                            } ?: textRuns.minByOrNull { run ->
                                abs(((run.bounds.top + run.bounds.bottom) / 2f) - startY)
                            }
                            if (hit != null) {
                                onSelectRun(hit)
                            }
                        }

                        else -> {
                            // Browse mode: do NOT consume touch events so LazyColumn scrolls smoothly at 144Hz!
                        }
                    }
                }
            }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${dimension.pageIndex + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }

        // Vector Annotation & Search Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Search hit highlights
            searchHits.forEach { hit ->
                drawRect(
                    color = Color(0xFFFB923C).copy(alpha = 0.35f),
                    topLeft = Offset(hit.bounds.left * w, hit.bounds.top * h),
                    size = Size(
                        width = (hit.bounds.right - hit.bounds.left) * w,
                        height = (hit.bounds.bottom - hit.bounds.top) * h
                    )
                )
            }

            // Text selection highlight
            if (selectedBounds != null) {
                drawRect(
                    color = Color(0xFF2563EB).copy(alpha = 0.28f),
                    topLeft = Offset(selectedBounds.left * w, selectedBounds.top * h),
                    size = Size(
                        width = (selectedBounds.right - selectedBounds.left) * w,
                        height = (selectedBounds.bottom - selectedBounds.top) * h
                    )
                )
            }

            // Persisted unflattened annotations
            annotations.forEach { ann ->
                val color = Color(ann.colorArgb)
                when (ann.type) {
                    PdfAnnotationType.HIGHLIGHT -> {
                        ann.bounds?.let { b ->
                            drawRect(
                                color = color.copy(alpha = 0.36f),
                                topLeft = Offset(b.left * w, b.top * h),
                                size = Size((b.right - b.left) * w, (b.bottom - b.top) * h)
                            )
                        }
                    }

                    PdfAnnotationType.UNDERLINE -> {
                        ann.bounds?.let { b ->
                            drawLine(
                                color = color,
                                start = Offset(b.left * w, b.bottom * h),
                                end = Offset(b.right * w, b.bottom * h),
                                strokeWidth = (ann.strokeWidthNormalized * w).coerceAtLeast(2f),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    PdfAnnotationType.INK, PdfAnnotationType.SIGNATURE -> {
                        if (ann.points.size >= 2) {
                            val path = Path().apply {
                                moveTo(ann.points.first().x * w, ann.points.first().y * h)
                                for (i in 1 until ann.points.size) {
                                    val p0 = ann.points[i - 1]
                                    val p1 = ann.points[i]
                                    quadraticTo(
                                        p0.x * w,
                                        p0.y * h,
                                        ((p0.x + p1.x) * 0.5f) * w,
                                        ((p0.y + p1.y) * 0.5f) * h
                                    )
                                }
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = (ann.strokeWidthNormalized * w).coerceAtLeast(2f),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    PdfAnnotationType.TEXT_BOX -> {
                        // Rendered below via crisp Compose Text overlay so font scaling stays sharp
                    }
                }
            }

            // Live drag highlight preview
            liveDragRect?.let { b ->
                drawRect(
                    color = Color(highlightColorArgb).copy(alpha = 0.38f),
                    topLeft = Offset(b.left * w, b.top * h),
                    size = Size((b.right - b.left) * w, (b.bottom - b.top) * h)
                )
            }

            // Live stylus/mouse ink stroke preview
            if (liveInkStroke.size >= 2) {
                val path = Path().apply {
                    moveTo(liveInkStroke.first().x * w, liveInkStroke.first().y * h)
                    for (i in 1 until liveInkStroke.size) {
                        val p0 = liveInkStroke[i - 1]
                        val p1 = liveInkStroke[i]
                        quadraticTo(
                            p0.x * w,
                            p0.y * h,
                            ((p0.x + p1.x) * 0.5f) * w,
                            ((p0.y + p1.y) * 0.5f) * h
                        )
                    }
                }
                drawPath(
                    path = path,
                    color = Color(inkColorArgb),
                    style = Stroke(
                        width = (strokeWidthNormalized * w).coerceAtLeast(2.5f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Crisp TextBox Annotation Overlays
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            annotations.filter { it.type == PdfAnnotationType.TEXT_BOX && it.bounds != null }
                .forEach { ann ->
                    val b = ann.bounds!!
                    Surface(
                        color = Color(0xFFFFFBEB).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .padding(0.dp)
                            .align(Alignment.TopStart)
                            .then(
                                Modifier.layoutOffset(
                                    x = (b.left * wPx).roundToInt(),
                                    y = (b.top * hPx).roundToInt()
                                )
                            )
                            .border(1.dp, Color(ann.colorArgb).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = ann.textContent,
                            color = Color(ann.colorArgb),
                            fontSize = ann.fontSizeSp.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
        }

        // Subtle Page Number Pill
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xCC0F172A),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Text(
                text = "${dimension.pageIndex + 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun Modifier.layoutOffset(x: Int, y: Int): Modifier =
    this.offset { IntOffset(x, y) }
