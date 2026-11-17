package com.example.ui.docx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxNoteKind
import com.example.domain.model.NormalizedPoint
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.WordDialogRequest
import com.example.ui.viewmodel.WorkspaceUiState
import com.example.ui.viewmodel.WorkspaceViewModel

@Composable
fun WordFindReplaceBar(
    uiState: WorkspaceUiState,
    onFindQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .padding(12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .testTag("word_find_replace_bar")
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.docxFindReplaceMode) Icons.Default.FindReplace else Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.docxFindReplaceMode) "Find & Replace (Ctrl+H)" else "Find in Document (Ctrl+F)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Find bar", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.docxFindQuery,
                    onValueChange = onFindQueryChange,
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("word_find_input")
                )
                val matchCount = uiState.docxFindMatchedBlockIds.size
                val matchText = if (matchCount == 0) "0" else "${uiState.docxFindMatchCursor + 1}/$matchCount"
                Text(
                    text = matchText,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelMedium
                )
                IconButton(onClick = onFindNext, modifier = Modifier.testTag("word_find_next")) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                }
            }

            if (uiState.docxFindReplaceMode) {
                OutlinedTextField(
                    value = uiState.docxReplaceQuery,
                    onValueChange = onReplaceQueryChange,
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("word_replace_input")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onReplaceCurrent,
                        enabled = uiState.docxFindMatchedBlockIds.isNotEmpty(),
                        modifier = Modifier.testTag("word_replace_one")
                    ) {
                        Text("Replace")
                    }
                    Button(
                        onClick = onReplaceAll,
                        enabled = uiState.docxFindQuery.isNotEmpty(),
                        modifier = Modifier.testTag("word_replace_all")
                    ) {
                        Text("Replace All")
                    }
                }
            }
        }
    }
}

@Composable
fun WordDialogsHost(
    dialogRequest: WordDialogRequest?,
    model: DocxDocumentModel?,
    viewModel: WorkspaceViewModel
) {
    if (dialogRequest == null || model == null) return
    val dismiss = { viewModel.openWordDialog(null) }

    when (dialogRequest) {
        WordDialogRequest.CUSTOM_MARGINS -> WordCustomMarginsDialog(
            model = model,
            onApply = viewModel::setDocxCustomMargins,
            onDismiss = dismiss
        )
        WordDialogRequest.HEADER_FOOTER -> WordHeaderFooterDialog(
            model = model,
            onApply = viewModel::updateDocxHeaderFooter,
            onDismiss = dismiss
        )
        WordDialogRequest.HYPERLINK -> WordHyperlinkDialog(
            onApply = viewModel::applyDocxHyperlink,
            onDismiss = dismiss
        )
        WordDialogRequest.COMMENT -> WordSimpleTextDialog(
            title = "Add Review Comment",
            label = "Comment on selected paragraph",
            confirmLabel = "Add Comment",
            onConfirm = viewModel::addDocxComment,
            onDismiss = dismiss
        )
        WordDialogRequest.DRAWING_CANVAS -> WordDrawingDialog(
            onInsert = viewModel::insertDocxDrawing,
            onDismiss = dismiss
        )
        WordDialogRequest.TEXT_BOX -> WordSimpleTextDialog(
            title = "Insert Callout Text Box",
            label = "Text box content",
            confirmLabel = "Insert Text Box",
            onConfirm = { viewModel.insertDocxTextBox(it, "0F548C") },
            onDismiss = dismiss
        )
        WordDialogRequest.EQUATION -> WordEquationDialog(
            onInsert = viewModel::insertDocxEquation,
            onDismiss = dismiss
        )
        WordDialogRequest.FOOTNOTE -> WordSimpleTextDialog(
            title = "Insert Footnote",
            label = "Footnote text (bottom of page)",
            confirmLabel = "Insert Footnote",
            onConfirm = { viewModel.insertDocxNote(DocxNoteKind.FOOTNOTE, it) },
            onDismiss = dismiss
        )
        WordDialogRequest.ENDNOTE -> WordSimpleTextDialog(
            title = "Insert Endnote",
            label = "Endnote text (end of document)",
            confirmLabel = "Insert Endnote",
            onConfirm = { viewModel.insertDocxNote(DocxNoteKind.ENDNOTE, it) },
            onDismiss = dismiss
        )
        WordDialogRequest.CITATION -> WordCitationDialog(
            onAdd = viewModel::addDocxCitation,
            onDismiss = dismiss
        )
    }
}

@Composable
private fun WordCustomMarginsDialog(
    model: DocxDocumentModel,
    onApply: (Float, Float, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var top by remember { mutableStateOf(model.margins.topInches.toString()) }
    var bottom by remember { mutableStateOf(model.margins.bottomInches.toString()) }
    var left by remember { mutableStateOf(model.margins.leftInches.toString()) }
    var right by remember { mutableStateOf(model.margins.rightInches.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Page Margins (inches)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(top, { top = it }, label = { Text("Top (in)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(bottom, { bottom = it }, label = { Text("Bottom (in)") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(left, { left = it }, label = { Text("Left (in)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(right, { right = it }, label = { Text("Right (in)") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(
                    top.toFloatOrNull() ?: model.margins.topInches,
                    bottom.toFloatOrNull() ?: model.margins.bottomInches,
                    left.toFloatOrNull() ?: model.margins.leftInches,
                    right.toFloatOrNull() ?: model.margins.rightInches
                )
            }) { Text("Apply Margins") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WordHeaderFooterDialog(
    model: DocxDocumentModel,
    onApply: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var header by remember { mutableStateOf(model.headerText) }
    var footer by remember { mutableStateOf(model.footerText) }
    var pageNums by remember { mutableStateOf(model.showPageNumbers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Header, Footer & Page Numbers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = header,
                    onValueChange = { header = it },
                    label = { Text("Running Header Text") },
                    placeholder = { Text("e.g., Confidential Technical Specification") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = footer,
                    onValueChange = { footer = it },
                    label = { Text("Running Footer Text") },
                    placeholder = { Text("e.g., DocuDesk Tablet Suite") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pageNums, onCheckedChange = { pageNums = it })
                    Text("Display Page Numbers in Footer", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(header, footer, pageNums) }) { Text("Save Header/Footer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WordHyperlinkDialog(
    onApply: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("https://") }
    var displayText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Hyperlink") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Target URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayText,
                    onValueChange = { displayText = it },
                    label = { Text("Link Display Text (optional)") },
                    placeholder = { Text("Leave blank to link entire paragraph") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(url, displayText.ifBlank { null }) }) { Text("Insert Link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WordSimpleTextDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordEquationDialog(
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var expr by remember { mutableStateOf("E = mc²") }
    val symbols = listOf("∑", "∫", "√", "π", "α", "β", "θ", "λ", "Δ", "≈", "≠", "≤", "≥", "∞", "±", "÷", "×", "→", "∈", "∀", "∃", "²", "³", "½")
    val templates = listOf(
        "Quadratic" to "x = (-b ± √(b² - 4ac)) / 2a",
        "Integral" to "∫₀^∞ e^(-x²) dx = √π / 2",
        "Sigma" to "∑ᵢ₌₁ⁿ i = n(n + 1) / 2",
        "Euler" to "e^(iπ) + 1 = 0"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equations & Mathematical Symbols") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = expr,
                    onValueChange = { expr = it },
                    label = { Text("Equation / Formula") },
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Symbol Palette", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    symbols.forEach { sym ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { expr += sym }
                        ) {
                            Text(
                                text = sym,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Text("Quick Templates", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    templates.forEach { (name, formula) ->
                        OutlinedButton(
                            onClick = { expr = formula },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onInsert(expr) }, enabled = expr.isNotBlank()) { Text("Insert Equation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WordDrawingDialog(
    onInsert: (String, List<List<NormalizedPoint>>, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("Stylus Diagram") }
    var selectedColorHex by remember { mutableStateOf("0F548C") }
    val strokes = remember { mutableStateListOf<List<NormalizedPoint>>() }
    var activeStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val colorMap = listOf(
        "0F548C" to Color(0xFF0F548C),
        "DC2626" to Color(0xFFDC2626),
        "15803D" to Color(0xFF15803D),
        "0F172A" to Color(0xFF0F172A),
        "D97706" to Color(0xFFD97706)
    )
    val inkColor = colorMap.firstOrNull { it.first == selectedColorHex }?.second ?: Color(0xFF0F548C)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shapes & Stylus Drawing Canvas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Drawing Title / Caption") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ink:", style = MaterialTheme.typography.labelMedium)
                    colorMap.forEach { (hex, col) ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (selectedColorHex == hex) 2.5.dp else 1.dp,
                                    color = if (selectedColorHex == hex) MaterialTheme.colorScheme.primary else Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear() }) { Text("Clear") }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val nx = (offset.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (offset.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    activeStroke = listOf(NormalizedPoint(nx, ny))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (change.position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    activeStroke = activeStroke + NormalizedPoint(nx, ny)
                                },
                                onDragEnd = {
                                    if (activeStroke.size >= 2) strokes.add(activeStroke)
                                    activeStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        (strokes + listOf(activeStroke)).forEach { stroke ->
                            if (stroke.size >= 2) {
                                val path = Path().apply {
                                    moveTo(stroke.first().x * size.width, stroke.first().y * size.height)
                                    stroke.drop(1).forEach { pt ->
                                        lineTo(pt.x * size.width, pt.y * size.height)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = inkColor,
                                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                    if (strokes.isEmpty() && activeStroke.isEmpty()) {
                        Text(
                            text = "Sketch diagram with Xiaomi stylus or finger",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onInsert(title, strokes.toList(), selectedColorHex) },
                enabled = strokes.isNotEmpty()
            ) { Text("Insert Drawing") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WordCitationDialog(
    onAdd: (tag: String, author: String, title: String, year: String, publisher: String, style: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tag by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2025") }
    var publisher by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("APA 7th") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Citation Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("APA 7th", "MLA", "IEEE").forEach { s ->
                        FilterChip(
                            selected = style == s,
                            onClick = { style = s },
                            label = { Text(s) }
                        )
                    }
                }
                OutlinedTextField(author, { author = it }, label = { Text("Author(s)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Source Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(year, { year = it }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(tag, { tag = it }, label = { Text("Short Tag") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(publisher, { publisher = it }, label = { Text("Publisher / Journal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(tag, author, title, year, publisher, style) },
                enabled = author.isNotBlank() && title.isNotBlank()
            ) { Text("Save Source") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
