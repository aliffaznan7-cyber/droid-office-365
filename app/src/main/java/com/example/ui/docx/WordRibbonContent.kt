package com.example.ui.docx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxMarginsPreset
import com.example.domain.model.DocxOrientation
import com.example.domain.model.DocxPaperSize
import com.example.domain.model.DocxPaperTone
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxScriptMode
import com.example.domain.model.DocxViewMode
import com.example.domain.model.RibbonTab
import com.example.ui.shell.RibbonActionButton
import com.example.ui.shell.RibbonGroup
import com.example.ui.viewmodel.DocxDocumentMutator
import com.example.ui.viewmodel.WordDialogRequest
import com.example.ui.viewmodel.WorkspaceUiState
import com.example.ui.viewmodel.WorkspaceViewModel
import kotlin.math.roundToInt

@Composable
fun WordRibbonContent(
    activeTab: RibbonTab,
    uiState: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    onOpenFile: () -> Unit,
    onNewDoc: () -> Unit,
    onSave: () -> Unit,
    onPrintDocument: () -> Unit,
    onInsertPicture: () -> Unit,
    onShowShortcuts: () -> Unit
) {
    val model = uiState.activeDocxModel
    val selectedBlock = model?.blocks?.firstOrNull { it.id == uiState.selectedDocxBlockId }
    val selectedPara = selectedBlock as? DocxBlock.Paragraph
    val selectedTable = selectedBlock as? DocxBlock.Table
    val firstRun = selectedPara?.runs?.firstOrNull()

    when (activeTab) {
        RibbonTab.HOME -> {
            RibbonGroup("File") {
                RibbonActionButton(Icons.Default.FileOpen, "Open", onOpenFile, "btn_word_open")
                RibbonActionButton(Icons.Default.Add, "New", onNewDoc, "btn_word_new")
                RibbonActionButton(Icons.Default.Save, "Save", onSave, "btn_word_save")
                RibbonActionButton(Icons.Default.Print, "Print", onPrintDocument, "btn_word_print")
            }

            RibbonGroup("Font") {
                var showFonts by remember { mutableStateOf(false) }
                var showSizes by remember { mutableStateOf(false) }
                var showTextColor by remember { mutableStateOf(false) }
                var showHighlight by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            OutlinedButton(
                                onClick = { showFonts = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_word_font_family")
                            ) {
                                Text(firstRun?.fontFamily ?: "Merriweather", style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = showFonts, onDismissRequest = { showFonts = false }) {
                                listOf("Merriweather", "Plus Jakarta Sans", "Inter", "JetBrains Mono", "Serif", "SansSerif").forEach { fam ->
                                    DropdownMenuItem(
                                        text = { Text(fam) },
                                        onClick = {
                                            viewModel.setDocxFontFamily(fam)
                                            showFonts = false
                                        }
                                    )
                                }
                            }
                        }

                        Box {
                            OutlinedButton(
                                onClick = { showSizes = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_word_font_size")
                            ) {
                                Text("${(firstRun?.fontSizeSp ?: 12f).roundToInt()} pt", style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = showSizes, onDismissRequest = { showSizes = false }) {
                                listOf(9f, 10f, 11f, 12f, 14f, 16f, 18f, 22f, 26f, 32f, 36f).forEach { sz ->
                                    DropdownMenuItem(
                                        text = { Text("${sz.toInt()} pt") },
                                        onClick = {
                                            viewModel.setDocxFontSize(sz)
                                            showSizes = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RibbonActionButton(Icons.Default.FormatBold, "B", { viewModel.toggleDocxFormatting(bold = true) }, "btn_word_bold", selected = firstRun?.bold == true)
                        RibbonActionButton(Icons.Default.FormatItalic, "I", { viewModel.toggleDocxFormatting(italic = true) }, "btn_word_italic", selected = firstRun?.italic == true)
                        RibbonActionButton(Icons.Default.FormatUnderlined, "U", { viewModel.toggleDocxFormatting(underline = true) }, "btn_word_underline", selected = firstRun?.underline == true)
                        RibbonActionButton(Icons.Default.StrikethroughS, "S", { viewModel.toggleDocxFormatting(strikethrough = true) }, "btn_word_strike", selected = firstRun?.strikethrough == true)
                        RibbonActionButton(Icons.Default.Subscript, "Sub", { viewModel.toggleDocxScriptMode(DocxScriptMode.SUBSCRIPT) }, "btn_word_sub", selected = firstRun?.scriptMode == DocxScriptMode.SUBSCRIPT)
                        RibbonActionButton(Icons.Default.Superscript, "Sup", { viewModel.toggleDocxScriptMode(DocxScriptMode.SUPERSCRIPT) }, "btn_word_sup", selected = firstRun?.scriptMode == DocxScriptMode.SUPERSCRIPT)

                        Box {
                            RibbonActionButton(Icons.Default.FormatColorText, "Color", { showTextColor = true }, "btn_word_color")
                            DropdownMenu(expanded = showTextColor, onDismissRequest = { showTextColor = false }) {
                                listOf(
                                    null to "Automatic",
                                    "0F172A" to "Obsidian",
                                    "1E3A8A" to "Cobalt Blue",
                                    "DC2626" to "Crimson Red",
                                    "15803D" to "Forest Green",
                                    "7C3AED" to "Royal Purple"
                                ).forEach { (hex, name) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(
                                                    Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(if (hex == null) Color.Black else Color(android.graphics.Color.parseColor("#$hex")))
                                                )
                                                Text(name)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setDocxTextColor(hex)
                                            showTextColor = false
                                        }
                                    )
                                }
                            }
                        }

                        Box {
                            RibbonActionButton(Icons.Default.FormatColorFill, "Highlt", { showHighlight = true }, "btn_word_highlight")
                            DropdownMenu(expanded = showHighlight, onDismissRequest = { showHighlight = false }) {
                                listOf(
                                    null to "No Highlight",
                                    "FEF08A" to "Yellow",
                                    "BBF7D0" to "Green",
                                    "BAE6FD" to "Cyan",
                                    "FBCFE8" to "Magenta"
                                ).forEach { (hex, name) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(
                                                    Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(if (hex == null) Color.Transparent else Color(android.graphics.Color.parseColor("#$hex")))
                                                        .border(1.dp, Color.Gray, CircleShape)
                                                )
                                                Text(name)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setDocxHighlightColor(hex)
                                            showHighlight = false
                                        }
                                    )
                                }
                            }
                        }

                        RibbonActionButton(Icons.Default.FormatClear, "Clear", viewModel::clearDocxFormatting, "btn_word_clear_fmt")
                    }
                }
            }

            RibbonGroup("Paragraph & Lists") {
                var showSpacing by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RibbonActionButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullets", { viewModel.setDocxParagraphStyle(DocxParagraphStyle.BULLET) }, "btn_word_bullets", selected = selectedPara?.style == DocxParagraphStyle.BULLET)
                        RibbonActionButton(Icons.Default.FormatListNumbered, "1-2-3", { viewModel.setDocxParagraphStyle(DocxParagraphStyle.NUMBERED) }, "btn_word_numbered", selected = selectedPara?.style == DocxParagraphStyle.NUMBERED)
                        RibbonActionButton(Icons.Default.ViewHeadline, "Multi", { viewModel.setDocxParagraphStyle(DocxParagraphStyle.MULTILEVEL) }, "btn_word_multilevel", selected = selectedPara?.style == DocxParagraphStyle.MULTILEVEL)
                        RibbonActionButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent", { viewModel.adjustDocxIndent(-18f, -1) }, "btn_word_outdent")
                        RibbonActionButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent", { viewModel.adjustDocxIndent(18f, 1) }, "btn_word_indent")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RibbonActionButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "Left", { viewModel.setDocxAlignment(DocxAlignment.LEFT) }, "btn_word_align_l", selected = selectedPara?.alignment == DocxAlignment.LEFT)
                        RibbonActionButton(Icons.Default.FormatAlignCenter, "Center", { viewModel.setDocxAlignment(DocxAlignment.CENTER) }, "btn_word_align_c", selected = selectedPara?.alignment == DocxAlignment.CENTER)
                        RibbonActionButton(Icons.AutoMirrored.Filled.FormatAlignRight, "Right", { viewModel.setDocxAlignment(DocxAlignment.RIGHT) }, "btn_word_align_r", selected = selectedPara?.alignment == DocxAlignment.RIGHT)
                        RibbonActionButton(Icons.Default.FormatAlignJustify, "Justify", { viewModel.setDocxAlignment(DocxAlignment.JUSTIFY) }, "btn_word_align_j", selected = selectedPara?.alignment == DocxAlignment.JUSTIFY)
                        Box {
                            RibbonActionButton(Icons.Default.FormatLineSpacing, "Spacing", { showSpacing = true }, "btn_word_line_spacing")
                            DropdownMenu(expanded = showSpacing, onDismissRequest = { showSpacing = false }) {
                                listOf(1.0f to "1.0 Single", 1.15f to "1.15 Standard", 1.5f to "1.5 Lines", 2.0f to "2.0 Double").forEach { (sp, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setDocxLineSpacing(sp)
                                            showSpacing = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            RibbonGroup("Styles Gallery") {
                var showMoreStyles by remember { mutableStateOf(false) }
                listOf(
                    DocxParagraphStyle.NORMAL to "Normal",
                    DocxParagraphStyle.TITLE to "Title",
                    DocxParagraphStyle.SUBTITLE to "Subtitle",
                    DocxParagraphStyle.HEADING_1 to "H1",
                    DocxParagraphStyle.HEADING_2 to "H2",
                    DocxParagraphStyle.HEADING_3 to "H3",
                    DocxParagraphStyle.QUOTE to "Quote"
                ).forEach { (style, shortLabel) ->
                    val active = selectedPara?.style == style
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .border(0.75.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .clickable { viewModel.setDocxParagraphStyle(style) }
                            .testTag("btn_style_${style.name.lowercase()}")
                    ) {
                        Text(
                            text = shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (style.name.startsWith("HEADING_") || style == DocxParagraphStyle.TITLE) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .clickable { showMoreStyles = true }
                    ) {
                        Text("H4–H6", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                    }
                    DropdownMenu(expanded = showMoreStyles, onDismissRequest = { showMoreStyles = false }) {
                        listOf(DocxParagraphStyle.HEADING_4, DocxParagraphStyle.HEADING_5, DocxParagraphStyle.HEADING_6).forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h.label) },
                                onClick = {
                                    viewModel.setDocxParagraphStyle(h)
                                    showMoreStyles = false
                                }
                            )
                        }
                    }
                }
            }

            RibbonGroup("Editing") {
                RibbonActionButton(Icons.Default.Search, "Find", { viewModel.openDocxFindReplace(false) }, "btn_word_find")
                RibbonActionButton(Icons.Default.FindReplace, "Replace", { viewModel.openDocxFindReplace(true) }, "btn_word_replace")
            }
        }

        RibbonTab.INSERT -> {
            RibbonGroup("Pages & Blocks") {
                RibbonActionButton(Icons.Default.Add, "Paragraph", { viewModel.insertDocxParagraph(DocxParagraphStyle.NORMAL) }, "btn_insert_para")
                RibbonActionButton(Icons.Default.HorizontalRule, "Page Break", viewModel::insertDocxPageBreak, "btn_insert_page_break")
            }

            RibbonGroup("Tables") {
                var showGridCreator by remember { mutableStateOf(false) }
                var gridRows by remember { mutableIntStateOf(3) }
                var gridCols by remember { mutableIntStateOf(3) }

                Box {
                    RibbonActionButton(Icons.Default.TableChart, "New Table", { showGridCreator = true }, "btn_insert_table_grid")
                    DropdownMenu(expanded = showGridCreator, onDismissRequest = { showGridCreator = false }) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Insert ${gridRows} × ${gridCols} Table", style = MaterialTheme.typography.titleSmall)
                            for (r in 1..6) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (c in 1..8) {
                                        val lit = r <= gridRows && c <= gridCols
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .border(0.75.dp, MaterialTheme.colorScheme.outline)
                                                .clickable {
                                                    gridRows = r
                                                    gridCols = c
                                                    viewModel.insertDocxTable(r, c)
                                                    showGridCreator = false
                                                }
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { gridRows = (gridRows + 1).coerceAtMost(8) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+Row") }
                                OutlinedButton(onClick = { gridCols = (gridCols + 1).coerceAtMost(8) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+Col") }
                            }
                        }
                    }
                }
                if (selectedTable != null) {
                    RibbonActionButton(Icons.Default.Add, "+Row", { viewModel.modifyDocxTableStructure(selectedTable.id, addRow = true) }, "btn_tbl_add_row")
                    RibbonActionButton(Icons.Default.Add, "+Col", { viewModel.modifyDocxTableStructure(selectedTable.id, addCol = true) }, "btn_tbl_add_col")
                    RibbonActionButton(Icons.Default.Close, "-Row", { viewModel.modifyDocxTableStructure(selectedTable.id, deleteLastRow = true) }, "btn_tbl_del_row")
                    RibbonActionButton(Icons.Default.Close, "-Col", { viewModel.modifyDocxTableStructure(selectedTable.id, deleteLastCol = true) }, "btn_tbl_del_col")
                }
            }

            RibbonGroup("Illustrations") {
                RibbonActionButton(Icons.Default.Image, "Picture", onInsertPicture, "btn_insert_picture")
                RibbonActionButton(Icons.Default.Brush, "Drawing", { viewModel.openWordDialog(WordDialogRequest.DRAWING_CANVAS) }, "btn_insert_drawing")
            }

            RibbonGroup("Links & Comments") {
                RibbonActionButton(Icons.Default.Link, "Hyperlink", { viewModel.openWordDialog(WordDialogRequest.HYPERLINK) }, "btn_insert_link")
                RibbonActionButton(Icons.Default.AddComment, "Comment", { viewModel.openWordDialog(WordDialogRequest.COMMENT) }, "btn_insert_comment")
            }

            RibbonGroup("Header & Footer") {
                RibbonActionButton(Icons.Default.ViewAgenda, "Header/Footer", { viewModel.openWordDialog(WordDialogRequest.HEADER_FOOTER) }, "btn_insert_hdr_ftr")
                RibbonActionButton(Icons.Default.Numbers, "Page #", viewModel::toggleDocxPageNumbers, "btn_insert_pg_num", selected = model?.showPageNumbers == true)
            }

            RibbonGroup("Text & Equations") {
                RibbonActionButton(Icons.Default.TextFields, "Text Box", { viewModel.openWordDialog(WordDialogRequest.TEXT_BOX) }, "btn_insert_textbox")
                RibbonActionButton(Icons.Default.Functions, "Equation", { viewModel.openWordDialog(WordDialogRequest.EQUATION) }, "btn_insert_equation")
            }
        }

        RibbonTab.LAYOUT -> {
            RibbonGroup("Page Margins") {
                var showMargins by remember { mutableStateOf(false) }
                Box {
                    RibbonActionButton(Icons.Default.BorderOuter, model?.margins?.preset?.label?.substringBefore(" ") ?: "Margins", { showMargins = true }, "btn_layout_margins")
                    DropdownMenu(expanded = showMargins, onDismissRequest = { showMargins = false }) {
                        DocxMarginsPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    showMargins = false
                                    if (preset == DocxMarginsPreset.CUSTOM) viewModel.openWordDialog(WordDialogRequest.CUSTOM_MARGINS)
                                    else viewModel.setDocxMarginsPreset(preset)
                                }
                            )
                        }
                    }
                }
            }

            RibbonGroup("Orientation & Size") {
                var showSize by remember { mutableStateOf(false) }
                RibbonActionButton(
                    Icons.Default.CropPortrait,
                    "Portrait",
                    { viewModel.setDocxOrientation(DocxOrientation.PORTRAIT) },
                    "btn_layout_portrait",
                    selected = model?.orientation == DocxOrientation.PORTRAIT
                )
                RibbonActionButton(
                    Icons.Default.CropLandscape,
                    "Landscape",
                    { viewModel.setDocxOrientation(DocxOrientation.LANDSCAPE) },
                    "btn_layout_landscape",
                    selected = model?.orientation == DocxOrientation.LANDSCAPE
                )
                Box {
                    RibbonActionButton(Icons.Default.AspectRatio, model?.paperSize?.name ?: "A4", { showSize = true }, "btn_layout_paper_size")
                    DropdownMenu(expanded = showSize, onDismissRequest = { showSize = false }) {
                        DocxPaperSize.entries.forEach { ps ->
                            DropdownMenuItem(
                                text = { Text(ps.label) },
                                onClick = {
                                    viewModel.setDocxPaperSize(ps)
                                    showSize = false
                                }
                            )
                        }
                    }
                }
            }

            RibbonGroup("Columns") {
                listOf(1, 2, 3).forEach { cols ->
                    RibbonActionButton(
                        Icons.Default.ViewColumn,
                        "$cols Col",
                        { viewModel.setDocxColumns(cols) },
                        "btn_layout_cols_$cols",
                        selected = model?.columnCount == cols
                    )
                }
            }

            RibbonGroup("Paragraph Spacing") {
                RibbonActionButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Indent -", { viewModel.adjustDocxIndent(-18f) }, "btn_layout_ind_dec")
                RibbonActionButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent +", { viewModel.adjustDocxIndent(18f) }, "btn_layout_ind_inc")
                RibbonActionButton(Icons.Default.FormatLineSpacing, "Before +4", { viewModel.adjustDocxParagraphSpacing(4f, 0f) }, "btn_layout_sp_before")
                RibbonActionButton(Icons.Default.FormatLineSpacing, "After +4", { viewModel.adjustDocxParagraphSpacing(0f, 4f) }, "btn_layout_sp_after")
            }
        }

        RibbonTab.REFERENCES -> {
            RibbonGroup("Table of Contents") {
                RibbonActionButton(Icons.AutoMirrored.Filled.Toc, "Insert / Update TOC", viewModel::insertOrRefreshTableOfContents, "btn_ref_toc")
            }
            RibbonGroup("Footnotes & Endnotes") {
                RibbonActionButton(Icons.Default.NoteAdd, "Insert Footnote", { viewModel.openWordDialog(WordDialogRequest.FOOTNOTE) }, "btn_ref_footnote")
                RibbonActionButton(Icons.Default.Description, "Insert Endnote", { viewModel.openWordDialog(WordDialogRequest.ENDNOTE) }, "btn_ref_endnote")
            }
            RibbonGroup("Citations & Bibliography") {
                RibbonActionButton(Icons.Default.MenuBook, "Add Citation", { viewModel.openWordDialog(WordDialogRequest.CITATION) }, "btn_ref_citation")
                RibbonActionButton(Icons.Default.Article, "Bibliography", viewModel::insertBibliographyBlock, "btn_ref_bibliography")
            }
        }

        RibbonTab.REVIEW -> {
            val (words, chars) = DocxDocumentMutator.computeWordAndCharCounts(model)
            RibbonGroup("Proofing") {
                RibbonActionButton(Icons.Default.Info, "$words W / $chars C", viewModel::toggleWordReviewPanel, "btn_rev_counts", selected = uiState.showWordReviewPanel)
                RibbonActionButton(Icons.Default.Search, "Find", { viewModel.openDocxFindReplace(false) }, "btn_rev_find")
            }
            RibbonGroup("Tracking") {
                RibbonActionButton(
                    Icons.Default.TrackChanges,
                    if (model?.trackChangesEnabled == true) "Track ON" else "Track Changes",
                    viewModel::toggleDocxTrackChanges,
                    "btn_rev_track",
                    selected = model?.trackChangesEnabled == true
                )
                RibbonActionButton(
                    Icons.Default.Visibility,
                    "Show Markup",
                    viewModel::toggleDocxShowMarkup,
                    "btn_rev_markup",
                    selected = model?.showMarkup == true
                )
                RibbonActionButton(Icons.Default.DoneAll, "Accept All", viewModel::acceptAllDocxTrackedChanges, "btn_rev_accept_all")
                RibbonActionButton(Icons.Default.Close, "Reject All", viewModel::rejectAllDocxTrackedChanges, "btn_rev_reject_all")
            }
            RibbonGroup("Comments & Modes") {
                RibbonActionButton(Icons.Default.AddComment, "New Comment", { viewModel.openWordDialog(WordDialogRequest.COMMENT) }, "btn_rev_new_comment")
                RibbonActionButton(Icons.Default.RateReview, "Review Pane", viewModel::toggleWordReviewPanel, "btn_rev_pane", selected = uiState.showWordReviewPanel)
                RibbonActionButton(Icons.Default.CenterFocusStrong, "Focus / Read", viewModel::toggleDocxFocusMode, "btn_rev_focus", selected = uiState.docxFocusMode)
            }
        }

        RibbonTab.VIEW -> {
            RibbonGroup("Document Views") {
                RibbonActionButton(
                    Icons.Default.Description,
                    "Print Layout",
                    { viewModel.setDocxViewMode(DocxViewMode.PRINT_LAYOUT) },
                    "btn_view_print_layout",
                    selected = uiState.docxViewMode == DocxViewMode.PRINT_LAYOUT
                )
                RibbonActionButton(
                    Icons.Default.Article,
                    "Web Layout",
                    { viewModel.setDocxViewMode(DocxViewMode.WEB_LAYOUT) },
                    "btn_view_web_layout",
                    selected = uiState.docxViewMode == DocxViewMode.WEB_LAYOUT
                )
                RibbonActionButton(Icons.Default.CenterFocusStrong, "Read Focus", viewModel::toggleDocxFocusMode, "btn_view_focus", selected = uiState.docxFocusMode)
            }
            RibbonGroup("Show") {
                RibbonActionButton(Icons.Default.Straighten, "Rulers", viewModel::toggleDocxRulers, "btn_view_rulers", selected = uiState.docxShowRulers)
                RibbonActionButton(Icons.Default.ViewSidebar, "Nav Pane", viewModel::toggleNavPane, "btn_view_nav", selected = uiState.isNavPaneOpen)
                RibbonActionButton(Icons.Default.Info, "Inspector", viewModel::toggleInspectorPane, "btn_view_inspector", selected = uiState.isInspectorOpen)
            }
            RibbonGroup("Zoom (50%–400%)") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Slider(
                        value = uiState.docxZoomScale,
                        onValueChange = viewModel::setDocxZoom,
                        valueRange = 0.5f..4.0f,
                        modifier = Modifier
                            .width(150.dp)
                            .testTag("word_zoom_slider")
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { viewModel.setDocxZoom(1.0f) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${(uiState.docxZoomScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            RibbonGroup("Page Comfort") {
                RibbonActionButton(
                    Icons.Default.Description,
                    "White",
                    { viewModel.setDocxPaperTone(DocxPaperTone.CRISP_WHITE) },
                    "btn_tone_white",
                    selected = uiState.docxPaperTone == DocxPaperTone.CRISP_WHITE
                )
                RibbonActionButton(
                    Icons.Default.MenuBook,
                    "Sepia",
                    { viewModel.setDocxPaperTone(DocxPaperTone.SEPIA_COMFORT) },
                    "btn_tone_sepia",
                    selected = uiState.docxPaperTone == DocxPaperTone.SEPIA_COMFORT
                )
                RibbonActionButton(
                    Icons.Default.DarkMode,
                    "Dark Paper",
                    { viewModel.setDocxPaperTone(DocxPaperTone.DARK_NIGHT) },
                    "btn_tone_dark",
                    selected = uiState.docxPaperTone == DocxPaperTone.DARK_NIGHT
                )
                RibbonActionButton(Icons.Default.Keyboard, "Shortcuts", onShowShortcuts, "btn_word_shortcuts")
            }
        }

        RibbonTab.PDF_ANNOTATE -> {}
    }
}
