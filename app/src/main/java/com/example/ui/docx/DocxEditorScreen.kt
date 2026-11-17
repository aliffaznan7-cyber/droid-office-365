package com.example.ui.docx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxOrientation
import com.example.domain.model.DocxPaperTone
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxViewMode
import com.example.ui.viewmodel.WordDialogRequest
import com.example.ui.viewmodel.WorkspaceUiState
import com.example.ui.viewmodel.WorkspaceViewModel

@Composable
fun DocxEditorScreen(
    uiState: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val model = uiState.activeDocxModel
    if (model == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading WordprocessingML document...", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val (paperBg, inkColor, backdropColor) = when (uiState.docxPaperTone) {
        DocxPaperTone.CRISP_WHITE -> Triple(Color.White, Color(0xFF1E293B), MaterialTheme.colorScheme.background)
        DocxPaperTone.SEPIA_COMFORT -> Triple(Color(0xFFFDF6E3), Color(0xFF3E3021), Color(0xFFECE3CF))
        DocxPaperTone.DARK_NIGHT -> Triple(Color(0xFF162032), Color(0xFFF1F5F9), Color(0xFF090E17))
    }

    val baseWidthIn = if (model.orientation == DocxOrientation.LANDSCAPE) {
        model.paperSize.heightIn
    } else {
        model.paperSize.widthIn
    }
    val zoom = uiState.docxZoomScale.coerceIn(0.5f, 4.0f)
    val sheetWidthDp = (baseWidthIn * 88f * zoom).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdropColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Optional Horizontal Ruler
            if (uiState.docxShowRulers && !uiState.docxFocusMode) {
                HorizontalInchRuler(
                    widthInches = baseWidthIn,
                    leftMarginIn = model.margins.leftInches,
                    rightMarginIn = model.margins.rightInches,
                    zoom = zoom
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Optional Vertical Ruler
                if (uiState.docxShowRulers && !uiState.docxFocusMode && uiState.docxViewMode == DocxViewMode.PRINT_LAYOUT) {
                    VerticalInchRuler(
                        topMarginIn = model.margins.topInches,
                        bottomMarginIn = model.margins.bottomInches
                    )
                }

                // Scrollable Paper Canvas (horizontalScroll supports Zoom > 100%)
                val hScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(if (uiState.docxViewMode == DocxViewMode.PRINT_LAYOUT) Modifier.horizontalScroll(hScroll) else Modifier),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = if (uiState.docxViewMode == DocxViewMode.WEB_LAYOUT) 20.dp else 32.dp,
                            vertical = 22.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxHeight()
                            .then(
                                if (uiState.docxViewMode == DocxViewMode.WEB_LAYOUT) Modifier.fillMaxWidth()
                                else Modifier.widthIn(min = sheetWidthDp)
                            )
                            .testTag("docx_editor_list")
                    ) {
                        item(key = "paper_sheet_container") {
                            val padLeft = (model.margins.leftInches * 38f * zoom.coerceIn(0.6f, 1.5f)).dp
                            val padRight = (model.margins.rightInches * 38f * zoom.coerceIn(0.6f, 1.5f)).dp
                            val padTop = (model.margins.topInches * 34f * zoom.coerceIn(0.6f, 1.5f)).dp
                            val padBottom = (model.margins.bottomInches * 34f * zoom.coerceIn(0.6f, 1.5f)).dp

                            Surface(
                                shape = RoundedCornerShape(if (uiState.docxViewMode == DocxViewMode.WEB_LAYOUT) 0.dp else 4.dp),
                                color = paperBg,
                                modifier = Modifier
                                    .then(
                                        if (uiState.docxViewMode == DocxViewMode.WEB_LAYOUT) Modifier.fillMaxWidth()
                                        else Modifier.width(sheetWidthDp)
                                    )
                                    .shadow(if (uiState.docxViewMode == DocxViewMode.WEB_LAYOUT) 0.dp else 8.dp, RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        start = padLeft,
                                        end = padRight,
                                        top = padTop,
                                        bottom = padBottom
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Running Header (Print Layout)
                                    if (uiState.docxViewMode == DocxViewMode.PRINT_LAYOUT && model.headerText.isNotBlank()) {
                                        Text(
                                            text = model.headerText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = inkColor.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        HorizontalDivider(color = inkColor.copy(alpha = 0.12f))
                                    }

                                    // Render blocks in 1, 2, or 3 Columns when in Print Layout
                                    val cols = if (uiState.docxViewMode == DocxViewMode.PRINT_LAYOUT) {
                                        model.columnCount.coerceIn(1, 3)
                                    } else 1

                                    if (cols == 1) {
                                        var listCounter = 0
                                        model.blocks.forEach { block ->
                                            listCounter = if (block is DocxBlock.Paragraph &&
                                                (block.style == DocxParagraphStyle.NUMBERED || block.style == DocxParagraphStyle.MULTILEVEL)
                                            ) listCounter + 1 else 0

                                            RenderSingleDocxBlock(
                                                block = block,
                                                listOrdinal = listCounter.coerceAtLeast(1),
                                                model = model,
                                                uiState = uiState,
                                                inkColor = inkColor,
                                                viewModel = viewModel
                                            )
                                        }
                                    } else {
                                        val chunked = model.blocks.chunked(cols)
                                        chunked.forEach { rowBlocks ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                rowBlocks.forEach { block ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        RenderSingleDocxBlock(
                                                            block = block,
                                                            listOrdinal = 1,
                                                            model = model,
                                                            uiState = uiState,
                                                            inkColor = inkColor,
                                                            viewModel = viewModel
                                                        )
                                                    }
                                                }
                                                repeat(cols - rowBlocks.size) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }

                                    // Footnotes & Endnotes section rendered on paper
                                    if (model.footnotes.isNotEmpty() || model.endnotes.isNotEmpty()) {
                                        Spacer(Modifier.height(14.dp))
                                        HorizontalDivider(
                                            modifier = Modifier.width(180.dp),
                                            color = inkColor.copy(alpha = 0.35f)
                                        )
                                        model.footnotes.forEach { note ->
                                            Text(
                                                text = "[${note.number}] ${note.text}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = inkColor.copy(alpha = 0.8f)
                                            )
                                        }
                                        model.endnotes.forEach { note ->
                                            Text(
                                                text = "[e${note.number}] ${note.text}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic,
                                                color = inkColor.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    // Quick Block Append Bar (hidden in Read/Focus Mode)
                                    if (!uiState.docxFocusMode) {
                                        Spacer(Modifier.height(14.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.insertDocxParagraph(DocxParagraphStyle.NORMAL) },
                                                modifier = Modifier.testTag("docx_add_paragraph")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Paragraph")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.insertDocxParagraph(DocxParagraphStyle.HEADING_2) },
                                                modifier = Modifier.testTag("docx_add_heading")
                                            ) {
                                                Icon(Icons.Default.Title, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Heading")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.insertDocxTable(3, 3) },
                                                modifier = Modifier.testTag("docx_add_table")
                                            ) {
                                                Icon(Icons.Default.TableChart, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Table")
                                            }
                                        }
                                    }

                                    // Running Footer & Page Number
                                    if (uiState.docxViewMode == DocxViewMode.PRINT_LAYOUT && (model.footerText.isNotBlank() || model.showPageNumbers)) {
                                        Spacer(Modifier.height(10.dp))
                                        HorizontalDivider(color = inkColor.copy(alpha = 0.12f))
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = model.footerText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = inkColor.copy(alpha = 0.55f)
                                            )
                                            if (model.showPageNumbers) {
                                                val totalPg = 1 + model.blocks.count { it is DocxBlock.PageBreak }
                                                Text(
                                                    text = "Page 1 of $totalPg",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = inkColor.copy(alpha = 0.55f)
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
        }

        // Floating Find & Replace Bar (Ctrl+F / Ctrl+H)
        if (uiState.showDocxFindReplaceBar) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                WordFindReplaceBar(
                    uiState = uiState,
                    onFindQueryChange = viewModel::updateDocxFindQuery,
                    onReplaceQueryChange = viewModel::updateDocxReplaceQuery,
                    onFindNext = viewModel::findNextDocxMatch,
                    onReplaceCurrent = viewModel::replaceCurrentDocxMatch,
                    onReplaceAll = viewModel::replaceAllDocxMatches,
                    onClose = viewModel::closeDocxFindReplace
                )
            }
        }

        // Exit Focus / Read Mode Pill
        if (uiState.docxFocusMode) {
            FloatingActionButton(
                onClick = viewModel::toggleDocxFocusMode,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .testTag("btn_exit_focus_mode")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Exit Focus Mode")
                    Spacer(Modifier.width(6.dp))
                    Text("Exit Focus", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Modal Dialogs Host
        WordDialogsHost(
            dialogRequest = uiState.activeWordDialog,
            model = model,
            viewModel = viewModel
        )
    }
}

@Composable
private fun RenderSingleDocxBlock(
    block: DocxBlock,
    listOrdinal: Int,
    model: DocxDocumentModel,
    uiState: WorkspaceUiState,
    inkColor: Color,
    viewModel: WorkspaceViewModel
) {
    val isSelected = block.id == uiState.selectedDocxBlockId
    val isFindMatch = uiState.docxFindMatchedBlockIds.contains(block.id)

    when (block) {
        is DocxBlock.Paragraph -> {
            DocxParagraphBlockView(
                block = block,
                listOrdinal = listOrdinal,
                isSelected = isSelected,
                isFindMatch = isFindMatch,
                showMarkup = model.showMarkup,
                defaultInkColor = inkColor,
                zoomScale = uiState.docxZoomScale,
                onFocus = { viewModel.selectDocxBlock(block.id) },
                onTextChange = { viewModel.updateDocxParagraphText(block.id, it) },
                onToggleBold = { viewModel.toggleDocxFormatting(bold = true) },
                onToggleItalic = { viewModel.toggleDocxFormatting(italic = true) },
                onOpenDialog = { viewModel.openWordDialog(it) },
                onDeleteBlock = { viewModel.deleteDocxBlock(block.id) }
            )
        }

        is DocxBlock.Table -> {
            DocxTableBlockView(
                block = block,
                isSelected = isSelected,
                defaultInkColor = inkColor,
                onSelect = { viewModel.selectDocxBlock(block.id) },
                onCellChange = { r, c, v -> viewModel.updateDocxTableCell(block.id, r, c, v) },
                onAddRow = { viewModel.modifyDocxTableStructure(block.id, addRow = true) },
                onAddCol = { viewModel.modifyDocxTableStructure(block.id, addCol = true) },
                onDeleteRow = { viewModel.modifyDocxTableStructure(block.id, deleteLastRow = true) },
                onDeleteCol = { viewModel.modifyDocxTableStructure(block.id, deleteLastCol = true) },
                onDeleteTable = { viewModel.deleteDocxBlock(block.id) }
            )
        }

        is DocxBlock.PageBreak -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = inkColor.copy(alpha = 0.25f))
                Text(
                    text = "  PAGE BREAK  ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = inkColor.copy(alpha = 0.5f)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = inkColor.copy(alpha = 0.25f))
            }
        }

        is DocxBlock.ImageBlock -> {
            DocxImageBlockView(
                block = block,
                isSelected = isSelected,
                defaultInkColor = inkColor,
                onSelect = { viewModel.selectDocxBlock(block.id) },
                onResize = { factor -> viewModel.resizeDocxImage(block.id, factor) },
                onDelete = { viewModel.deleteDocxBlock(block.id) }
            )
        }

        is DocxBlock.DrawingBlock -> {
            DocxDrawingBlockView(
                block = block,
                isSelected = isSelected,
                onSelect = { viewModel.selectDocxBlock(block.id) },
                onDelete = { viewModel.deleteDocxBlock(block.id) }
            )
        }

        is DocxBlock.TextBoxBlock -> {
            DocxTextBoxBlockView(
                block = block,
                isSelected = isSelected,
                defaultInkColor = inkColor,
                onSelect = { viewModel.selectDocxBlock(block.id) },
                onDelete = { viewModel.deleteDocxBlock(block.id) }
            )
        }

        is DocxBlock.EquationBlock -> {
            DocxEquationBlockView(
                block = block,
                isSelected = isSelected,
                defaultInkColor = inkColor,
                onSelect = { viewModel.selectDocxBlock(block.id) },
                onDelete = { viewModel.deleteDocxBlock(block.id) }
            )
        }

        is DocxBlock.TableOfContentsBlock -> {
            DocxTableOfContentsView(
                block = block,
                model = model,
                defaultInkColor = inkColor,
                onJumpToBlock = { viewModel.selectDocxBlock(it) }
            )
        }
    }
}

@Composable
private fun HorizontalInchRuler(
    widthInches: Float,
    leftMarginIn: Float,
    rightMarginIn: Float,
    zoom: Float
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLow
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val marginColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(bg)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalInches = widthInches.coerceAtLeast(6f)
            val pxPerInch = (size.width / totalInches).coerceAtLeast(30f)
            val leftPx = (leftMarginIn * pxPerInch).coerceAtMost(size.width * 0.3f)
            val rightPx = (rightMarginIn * pxPerInch).coerceAtMost(size.width * 0.3f)

            drawRect(marginColor, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(leftPx, size.height))
            drawRect(marginColor, topLeft = Offset(size.width - rightPx, 0f), size = androidx.compose.ui.geometry.Size(rightPx, size.height))

            val maxInch = totalInches.toInt()
            for (i in 0..maxInch * 2) {
                val x = i * pxPerInch * 0.5f
                if (x <= size.width) {
                    val h = if (i % 2 == 0) size.height * 0.65f else size.height * 0.35f
                    drawLine(tickColor, start = Offset(x, size.height - h), end = Offset(x, size.height), strokeWidth = 1.2f)
                }
            }
        }
        Text(
            text = "Ruler (${(zoom * 100).toInt()}% • L:${leftMarginIn}\" R:${rightMarginIn}\")",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        )
    }
}

@Composable
private fun VerticalInchRuler(
    topMarginIn: Float,
    bottomMarginIn: Float
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLow
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val marginColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight()
            .background(bg)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        val pxPerInch = size.height / 11f
        drawRect(marginColor, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, topMarginIn * pxPerInch))
        drawRect(marginColor, topLeft = Offset(0f, size.height - bottomMarginIn * pxPerInch), size = androidx.compose.ui.geometry.Size(size.width, bottomMarginIn * pxPerInch))
        for (i in 0..22) {
            val y = i * pxPerInch * 0.5f
            if (y <= size.height) {
                val w = if (i % 2 == 0) size.width * 0.6f else size.width * 0.3f
                drawLine(tickColor, start = Offset(size.width - w, y), end = Offset(size.width, y), strokeWidth = 1.2f)
            }
        }
    }
}
