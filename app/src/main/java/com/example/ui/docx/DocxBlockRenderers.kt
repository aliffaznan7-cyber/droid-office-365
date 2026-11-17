package com.example.ui.docx

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxScriptMode
import com.example.domain.model.DocxTextRun
import com.example.domain.model.DocxTrackChangeType
import com.example.ui.theme.FluentCobalt
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MerriweatherFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.viewmodel.WordDialogRequest
import java.io.File

internal fun mapDocxFontFamily(name: String): FontFamily = when (name.lowercase()) {
    "plus jakarta sans", "jakarta" -> PlusJakartaSansFontFamily
    "inter" -> InterFontFamily
    "jetbrains mono", "monospace", "courier new" -> JetBrainsMonoFontFamily
    "serif" -> FontFamily.Serif
    "sansserif", "sans-serif", "arial", "calibri" -> FontFamily.SansSerif
    else -> MerriweatherFontFamily
}

internal fun parseHexOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor("#${hex.trimStart('#')}")) }.getOrNull()
}

@Composable
internal fun DocxParagraphBlockView(
    block: DocxBlock.Paragraph,
    listOrdinal: Int,
    isSelected: Boolean,
    isFindMatch: Boolean,
    showMarkup: Boolean,
    defaultInkColor: Color,
    zoomScale: Float,
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onOpenDialog: (WordDialogRequest) -> Unit,
    onDeleteBlock: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val visibleRuns = remember(block.runs, showMarkup) {
        if (showMarkup) block.runs
        else block.runs.filter { it.trackChangeType != DocxTrackChangeType.DELETION }
    }
    val combinedText = visibleRuns.joinToString("") { it.text }
    val firstRun = visibleRuns.firstOrNull() ?: block.runs.firstOrNull()

    val baseFontSize = when (block.style) {
        DocxParagraphStyle.TITLE -> 28f
        DocxParagraphStyle.SUBTITLE -> 16f
        DocxParagraphStyle.HEADING_1 -> 22f
        DocxParagraphStyle.HEADING_2 -> 18f
        DocxParagraphStyle.HEADING_3 -> 15f
        DocxParagraphStyle.HEADING_4 -> 14f
        DocxParagraphStyle.HEADING_5 -> 13f
        DocxParagraphStyle.HEADING_6 -> 12f
        else -> firstRun?.fontSizeSp ?: 12f
    }
    val scaledSp = (baseFontSize * zoomScale.coerceIn(0.6f, 2.2f)).sp

    val isHeading = block.style.name.startsWith("HEADING_") || block.style == DocxParagraphStyle.TITLE
    val fontFamily = mapDocxFontFamily(
        firstRun?.fontFamily ?: if (isHeading) "Plus Jakarta Sans" else "Merriweather"
    )
    val textAlign = when (block.alignment) {
        DocxAlignment.LEFT -> TextAlign.Start
        DocxAlignment.CENTER -> TextAlign.Center
        DocxAlignment.RIGHT -> TextAlign.End
        DocxAlignment.JUSTIFY -> TextAlign.Justify
    }

    val decorList = buildList {
        if (firstRun?.underline == true || !firstRun?.hyperlinkUrl.isNullOrBlank()) add(TextDecoration.Underline)
        if (firstRun?.strikethrough == true) add(TextDecoration.LineThrough)
    }
    val textDecoration = if (decorList.isEmpty()) TextDecoration.None else TextDecoration.combine(decorList)

    val resolvedColor = parseHexOrNull(firstRun?.colorHex) ?: when (block.style) {
        DocxParagraphStyle.TITLE, DocxParagraphStyle.HEADING_1 -> if (defaultInkColor == Color(0xFFF1F5F9)) Color(0xFF93C5FD) else Color(0xFF0F294A)
        DocxParagraphStyle.HEADING_2, DocxParagraphStyle.HEADING_3 -> if (defaultInkColor == Color(0xFFF1F5F9)) Color(0xFF7CC4FA) else Color(0xFF1E3A8A)
        DocxParagraphStyle.SUBTITLE -> defaultInkColor.copy(alpha = 0.72f)
        DocxParagraphStyle.QUOTE -> defaultInkColor.copy(alpha = 0.85f)
        else -> defaultInkColor
    }

    val style = TextStyle(
        fontFamily = fontFamily,
        fontSize = scaledSp,
        lineHeight = scaledSp * block.lineSpacingMultiplier.coerceIn(1.0f, 2.5f) * 1.25f,
        fontWeight = if (isHeading || firstRun?.bold == true) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (firstRun?.italic == true || block.style == DocxParagraphStyle.QUOTE || block.style == DocxParagraphStyle.SUBTITLE) {
            FontStyle.Italic
        } else FontStyle.Normal,
        textDecoration = textDecoration,
        baselineShift = when (firstRun?.scriptMode) {
            DocxScriptMode.SUPERSCRIPT -> BaselineShift.Superscript
            DocxScriptMode.SUBSCRIPT -> BaselineShift.Subscript
            else -> BaselineShift.None
        },
        color = resolvedColor,
        background = parseHexOrNull(firstRun?.highlightColorHex) ?: Color.Transparent,
        textAlign = textAlign
    )

    val indentDp = ((block.leftIndentPt + block.listLevel * 20f) * zoomScale.coerceIn(0.6f, 2.0f)).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = block.spaceBeforePt.dp,
                bottom = block.spaceAfterPt.dp,
                start = indentDp
            )
            .pointerInput(block.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onFocus()
                            showContextMenu = true
                        }
                    }
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        block.style == DocxParagraphStyle.QUOTE -> Modifier
                            .background(FluentCobalt.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                            .border(1.dp, FluentCobalt.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                        isFindMatch -> Modifier
                            .background(Color(0xFFFEF08A).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFCA8A04), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                        isSelected -> Modifier
                            .background(FluentCobalt.copy(alpha = 0.07f), RoundedCornerShape(4.dp))
                            .border(1.dp, FluentCobalt.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                        else -> Modifier.padding(6.dp)
                    }
                )
        ) {
            when (block.style) {
                DocxParagraphStyle.BULLET -> {
                    val bulletGlyph = when (block.listLevel) {
                        1 -> "◦"
                        2 -> "▪"
                        else -> "•"
                    }
                    Text(text = bulletGlyph, style = style, modifier = Modifier.padding(end = 10.dp))
                }
                DocxParagraphStyle.NUMBERED, DocxParagraphStyle.MULTILEVEL -> {
                    val marker = when {
                        block.style == DocxParagraphStyle.MULTILEVEL && block.listLevel == 1 -> "${('a' + (listOrdinal - 1) % 26)})"
                        block.style == DocxParagraphStyle.MULTILEVEL && block.listLevel >= 2 -> "i."
                        else -> "$listOrdinal."
                    }
                    Text(text = marker, style = style, modifier = Modifier.padding(end = 8.dp))
                }
                else -> {}
            }

            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = combinedText,
                    onValueChange = onTextChange,
                    textStyle = style,
                    cursorBrush = SolidColor(FluentCobalt),
                    visualTransformation = { text ->
                        if (!showMarkup || visibleRuns.none { it.trackChangeType != DocxTrackChangeType.NONE }) {
                            TransformedText(text, OffsetMapping.Identity)
                        } else {
                            TransformedText(buildTrackedAnnotatedString(visibleRuns), OffsetMapping.Identity)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChangedCompat(onFocus)
                        .testTag("docx_paragraph_${block.id}")
                )

                // Metadata chips (Hyperlinks, Comments, Footnotes/Endnotes)
                val links = visibleRuns.mapNotNull { it.hyperlinkUrl }.distinct()
                if (links.isNotEmpty() || block.commentIds.isNotEmpty() || block.footnoteIds.isNotEmpty() || block.endnoteIds.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        links.forEach { url ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FluentCobalt.copy(alpha = 0.10f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = FluentCobalt, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(url, style = MaterialTheme.typography.labelSmall, color = FluentCobalt)
                                }
                            }
                        }
                        if (block.commentIds.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF08A)) {
                                Text(
                                    text = "💬 ${block.commentIds.joinToString(",") { "#$it" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF713F12),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        block.footnoteIds.forEach { fid ->
                            Text("[$fid]", style = MaterialTheme.typography.labelSmall, color = FluentCobalt)
                        }
                        block.endnoteIds.forEach { eid ->
                            Text("[e$eid]", style = MaterialTheme.typography.labelSmall, color = FluentCobalt)
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
            DropdownMenuItem(text = { Text("Toggle Bold (Ctrl+B)") }, onClick = { showContextMenu = false; onToggleBold() })
            DropdownMenuItem(text = { Text("Toggle Italic (Ctrl+I)") }, onClick = { showContextMenu = false; onToggleItalic() })
            DropdownMenuItem(
                text = { Text("Add Comment") },
                leadingIcon = { Icon(Icons.Default.AddComment, null) },
                onClick = { showContextMenu = false; onOpenDialog(WordDialogRequest.COMMENT) }
            )
            DropdownMenuItem(
                text = { Text("Insert Hyperlink") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                onClick = { showContextMenu = false; onOpenDialog(WordDialogRequest.HYPERLINK) }
            )
            DropdownMenuItem(
                text = { Text("Delete Paragraph") },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                onClick = { showContextMenu = false; onDeleteBlock() }
            )
        }
    }
}

private fun buildTrackedAnnotatedString(runs: List<DocxTextRun>): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        val start = length
        append(run.text)
        val end = length
        when (run.trackChangeType) {
            DocxTrackChangeType.INSERTION -> addStyle(
                SpanStyle(
                    color = Color(0xFF15803D),
                    background = Color(0xFFDCFCE7),
                    textDecoration = TextDecoration.Underline
                ),
                start,
                end
            )
            DocxTrackChangeType.DELETION -> addStyle(
                SpanStyle(
                    color = Color(0xFFDC2626),
                    background = Color(0xFFFEE2E2),
                    textDecoration = TextDecoration.LineThrough
                ),
                start,
                end
            )
            DocxTrackChangeType.NONE -> {}
        }
    }
}

@Composable
private fun Modifier.onFocusChangedCompat(onFocus: () -> Unit): Modifier =
    this.onFocusChanged { if (it.isFocused) onFocus() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocxTableBlockView(
    block: DocxBlock.Table,
    isSelected: Boolean,
    defaultInkColor: Color,
    onSelect: () -> Unit,
    onCellChange: (Int, Int, String) -> Unit,
    onAddRow: () -> Unit,
    onAddCol: () -> Unit,
    onDeleteRow: () -> Unit,
    onDeleteCol: () -> Unit,
    onDeleteTable: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(block.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onSelect()
                            showMenu = true
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = {
                        onSelect()
                        showMenu = true
                    }
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) FluentCobalt else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            val hdrColor = parseHexOrNull(block.headerShadingHex) ?: Color(0xFF1E3A8A)
            block.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (rIdx == 0) hdrColor else Color.Transparent)
                ) {
                    row.forEachIndexed { cIdx, cellText ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, Color(0xFFCBD5E1))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            BasicTextField(
                                value = cellText,
                                onValueChange = { onCellChange(rIdx, cIdx, it) },
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (rIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (rIdx == 0) Color.White else defaultInkColor
                                ),
                                cursorBrush = SolidColor(if (rIdx == 0) Color.White else FluentCobalt),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChangedCompat(onSelect)
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Insert Row Below") }, onClick = { showMenu = false; onAddRow() })
            DropdownMenuItem(text = { Text("Insert Column Right") }, onClick = { showMenu = false; onAddCol() })
            DropdownMenuItem(text = { Text("Delete Last Row") }, onClick = { showMenu = false; onDeleteRow() })
            DropdownMenuItem(text = { Text("Delete Last Column") }, onClick = { showMenu = false; onDeleteCol() })
            DropdownMenuItem(
                text = { Text("Delete Table") },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                onClick = { showMenu = false; onDeleteTable() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocxImageBlockView(
    block: DocxBlock.ImageBlock,
    isSelected: Boolean,
    defaultInkColor: Color,
    onSelect: () -> Unit,
    onResize: (Float) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val bitmap = remember(block.localFilePath) {
        val f = File(block.localFilePath)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(block.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onSelect()
                            showMenu = true
                        }
                    }
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = {
                        onSelect()
                        showMenu = true
                    }
                )
                .border(
                    width = if (isSelected) 2.dp else 0.75.dp,
                    color = if (isSelected) FluentCobalt else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(8.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = block.caption.ifBlank { block.mediaFileName },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(block.widthDp.dp)
                        .height(block.heightDp.dp)
                )
            } else {
                Text(
                    text = "[Embedded Image: ${block.mediaFileName}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = defaultInkColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(24.dp)
                )
            }
            if (block.caption.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = block.caption,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = defaultInkColor.copy(alpha = 0.75f)
                )
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Enlarge Image (+20%)") }, onClick = { showMenu = false; onResize(1.2f) })
            DropdownMenuItem(text = { Text("Shrink Image (-20%)") }, onClick = { showMenu = false; onResize(0.8f) })
            DropdownMenuItem(
                text = { Text("Remove Image") },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}

@Composable
internal fun DocxDrawingBlockView(
    block: DocxBlock.DrawingBlock,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val inkColor = parseHexOrNull(block.colorHex) ?: FluentCobalt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) FluentCobalt else Color(0xFFCBD5E1),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(block.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (isSelected) {
                Text("Delete", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), modifier = Modifier.clickable { onDelete() })
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(block.heightDp.dp)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
        ) {
            block.strokes.forEach { stroke ->
                if (stroke.size >= 2) {
                    val path = Path().apply {
                        moveTo(stroke.first().x * size.width, stroke.first().y * size.height)
                        stroke.drop(1).forEach { pt -> lineTo(pt.x * size.width, pt.y * size.height) }
                    }
                    drawPath(
                        path = path,
                        color = inkColor,
                        style = Stroke(width = block.strokeWidthPt * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DocxEquationBlockView(
    block: DocxBlock.EquationBlock,
    isSelected: Boolean,
    defaultInkColor: Color,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = FluentCobalt.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(if (isSelected) 1.5.dp else 0.75.dp, FluentCobalt.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Functions, contentDescription = null, tint = FluentCobalt, modifier = Modifier.size(18.dp))
            Text(
                text = block.displayUnicode,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 15.sp,
                color = defaultInkColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            if (isSelected) {
                Text("Remove", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), modifier = Modifier.clickable { onDelete() })
            }
        }
    }
}

@Composable
internal fun DocxTextBoxBlockView(
    block: DocxBlock.TextBoxBlock,
    isSelected: Boolean,
    defaultInkColor: Color,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val borderCol = parseHexOrNull(block.borderColorHex) ?: FluentCobalt
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = borderCol.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(if (isSelected) 2.dp else 1.25.dp, borderCol, RoundedCornerShape(6.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = defaultInkColor,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Text("Remove", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), modifier = Modifier.clickable { onDelete() })
            }
        }
    }
}

@Composable
internal fun DocxTableOfContentsView(
    block: DocxBlock.TableOfContentsBlock,
    model: DocxDocumentModel,
    defaultInkColor: Color,
    onJumpToBlock: (String) -> Unit
) {
    val headings = model.blocks.filterIsInstance<DocxBlock.Paragraph>().filter {
        it.style.name.startsWith("HEADING_") || it.style == DocxParagraphStyle.TITLE
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = FluentCobalt.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FluentCobalt.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FluentCobalt
            )
            HorizontalDivider(color = FluentCobalt.copy(alpha = 0.2f))
            if (headings.isEmpty()) {
                Text(
                    text = "Apply Heading 1–6 styles to paragraphs to populate Table of Contents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = defaultInkColor.copy(alpha = 0.7f)
                )
            } else {
                headings.forEach { h ->
                    val level = h.style.name.removePrefix("HEADING_").toIntOrNull() ?: 1
                    val title = h.runs.joinToString("") { it.text }.ifBlank { "(Untitled heading)" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpToBlock(h.id) }
                            .padding(start = ((level - 1).coerceAtLeast(0) * 14).dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = defaultInkColor)
                        Text(text = "H$level", style = MaterialTheme.typography.labelSmall, color = FluentCobalt)
                    }
                }
            }
        }
    }
}
