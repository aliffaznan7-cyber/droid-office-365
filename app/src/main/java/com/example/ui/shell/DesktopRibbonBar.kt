package com.example.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DocumentFormat
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.PdfEditorTool
import com.example.domain.model.RibbonTab
import com.example.ui.docx.WordRibbonContent
import com.example.ui.theme.FluentCobalt
import com.example.ui.theme.FormatPdfColor
import com.example.ui.viewmodel.WorkspaceUiState
import com.example.ui.viewmodel.WorkspaceViewModel

@Composable
fun DesktopRibbonBar(
    uiState: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    onSelectRibbonTab: (RibbonTab) -> Unit,
    onToggleRibbonExpanded: () -> Unit,
    onOpenFile: () -> Unit,
    onNewDoc: () -> Unit,
    onSave: () -> Unit,
    onExportFlattenPdf: () -> Unit,
    onPrintDocument: () -> Unit,
    onInsertPicture: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onToggleNavPane: () -> Unit,
    onToggleInspector: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onShowShortcuts: () -> Unit,
    onSelectPdfTool: (PdfEditorTool) -> Unit,
    onSelectInkColor: (Long) -> Unit,
    onSelectStrokeWidth: (Float) -> Unit,
    onOpenSignaturePad: () -> Unit,
    onZoomDelta: (Float) -> Unit,
    onXlsxBold: () -> Unit,
    onXlsxCurrency: () -> Unit,
    onXlsxAddSheet: () -> Unit
) {
    val activeFormat = uiState.activeTab?.format

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Ribbon Tab Header Row + Quick Access Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Quick Access Toolbar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onToggleNavPane,
                        modifier = Modifier.testTag("ribbon_toggle_nav_pane")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewSidebar,
                            contentDescription = "Toggle navigation pane",
                            tint = if (uiState.isNavPaneOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.testTag("ribbon_quick_save")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save (Ctrl+S)")
                    }
                    IconButton(
                        onClick = onUndo,
                        modifier = Modifier.testTag("ribbon_quick_undo")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo (Ctrl+Z)")
                    }
                    IconButton(
                        onClick = onRedo,
                        modifier = Modifier.testTag("ribbon_quick_redo")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo (Ctrl+Y)")
                    }
                }

                Spacer(Modifier.width(8.dp))
                VerticalDivider(modifier = Modifier.height(22.dp))
                Spacer(Modifier.width(8.dp))

                // Ribbon Tabs
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RibbonTab.entries.forEach { tab ->
                        if (tab == RibbonTab.PDF_ANNOTATE && activeFormat != DocumentFormat.PDF) {
                            return@forEach
                        }
                        if (tab == RibbonTab.REFERENCES && activeFormat != DocumentFormat.DOCX) {
                            return@forEach
                        }
                        val selected = uiState.activeRibbonTab == tab && uiState.isRibbonExpanded
                        val isContextual = tab == RibbonTab.PDF_ANNOTATE
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                selected && isContextual -> FormatPdfColor.copy(alpha = 0.14f)
                                selected -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            },
                            modifier = Modifier
                                .clickable { onSelectRibbonTab(tab) }
                                .testTag("ribbon_tab_${tab.name.lowercase()}")
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = when {
                                    selected && isContextual -> FormatPdfColor
                                    selected -> MaterialTheme.colorScheme.primary
                                    isContextual -> FormatPdfColor
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Right-side Quick Toggles
                IconButton(
                    onClick = onShowShortcuts,
                    modifier = Modifier.testTag("ribbon_shortcuts_button")
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard shortcuts")
                }
                IconButton(
                    onClick = onToggleInspector,
                    modifier = Modifier.testTag("ribbon_toggle_inspector")
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Toggle document inspector",
                        tint = if (uiState.isInspectorOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleRibbonExpanded,
                    modifier = Modifier.testTag("ribbon_collapse_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isRibbonExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle ribbon"
                    )
                }
            }

            // Collapsible Ribbon Command Surface
            AnimatedVisibility(visible = uiState.isRibbonExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (activeFormat == DocumentFormat.DOCX && uiState.activeRibbonTab != RibbonTab.PDF_ANNOTATE) {
                            WordRibbonContent(
                                activeTab = uiState.activeRibbonTab,
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenFile = onOpenFile,
                                onNewDoc = onNewDoc,
                                onSave = onSave,
                                onPrintDocument = onPrintDocument,
                                onInsertPicture = onInsertPicture,
                                onShowShortcuts = onShowShortcuts
                            )
                        } else when (uiState.activeRibbonTab) {
                            RibbonTab.HOME -> {
                                RibbonGroup("File") {
                                    RibbonActionButton(Icons.Default.FileOpen, "Open", onOpenFile, "btn_open")
                                    RibbonActionButton(Icons.Default.Add, "New", onNewDoc, "btn_new")
                                    RibbonActionButton(Icons.Default.Save, "Save", onSave, "btn_save")
                                }
                                if (activeFormat == DocumentFormat.PDF) {
                                    RibbonGroup("PDF Vector") {
                                        RibbonActionButton(
                                            Icons.Default.PanTool,
                                            "Browse",
                                            { onSelectPdfTool(PdfEditorTool.BROWSE) },
                                            "btn_pdf_browse",
                                            selected = uiState.activePdfTool == PdfEditorTool.BROWSE
                                        )
                                        RibbonActionButton(
                                            Icons.Default.Highlight,
                                            "Highlight",
                                            { onSelectPdfTool(PdfEditorTool.HIGHLIGHT) },
                                            "btn_pdf_highlight",
                                            selected = uiState.activePdfTool == PdfEditorTool.HIGHLIGHT
                                        )
                                        RibbonActionButton(
                                            Icons.Default.Brush,
                                            "Stylus Ink",
                                            { onSelectPdfTool(PdfEditorTool.INK_PEN) },
                                            "btn_pdf_ink",
                                            selected = uiState.activePdfTool == PdfEditorTool.INK_PEN
                                        )
                                        RibbonActionButton(
                                            Icons.Default.SaveAs,
                                            "Flatten PDF",
                                            onExportFlattenPdf,
                                            "btn_pdf_flatten"
                                        )
                                    }
                                } else if (activeFormat == DocumentFormat.XLSX) {
                                    RibbonGroup("Cells & Format") {
                                        RibbonActionButton(Icons.Default.FormatBold, "Bold", onXlsxBold, "btn_xlsx_bold")
                                        RibbonActionButton(Icons.Default.BookmarkAdded, "RM Currency", onXlsxCurrency, "btn_xlsx_currency")
                                        RibbonActionButton(Icons.Default.Add, "Add Sheet", onXlsxAddSheet, "btn_xlsx_sheet")
                                    }
                                }
                                RibbonGroup("Navigate") {
                                    RibbonActionButton(Icons.Default.Search, "Find", onFind, "btn_find")
                                }
                            }

                            RibbonTab.INSERT -> {
                                when (activeFormat) {
                                    DocumentFormat.PDF -> {
                                        RibbonGroup("PDF Elements") {
                                            RibbonActionButton(
                                                Icons.Default.TextFields,
                                                "Text Box",
                                                { onSelectPdfTool(PdfEditorTool.TEXT_BOX) },
                                                "btn_insert_textbox",
                                                selected = uiState.activePdfTool == PdfEditorTool.TEXT_BOX
                                            )
                                            RibbonActionButton(
                                                Icons.Default.Draw,
                                                "Signature",
                                                onOpenSignaturePad,
                                                "btn_insert_signature"
                                            )
                                        }
                                    }
                                    DocumentFormat.XLSX -> {
                                        RibbonGroup("Workbook") {
                                            RibbonActionButton(Icons.Default.Add, "Worksheet", onXlsxAddSheet, "btn_insert_sheet")
                                        }
                                    }
                                    else -> {}
                                }
                            }

                            RibbonTab.LAYOUT -> {
                                RibbonGroup("Workspace Panes") {
                                    RibbonActionButton(Icons.Default.ViewSidebar, "Nav Pane", onToggleNavPane, "btn_layout_nav", selected = uiState.isNavPaneOpen)
                                    RibbonActionButton(Icons.Default.Info, "Inspector", onToggleInspector, "btn_layout_inspector", selected = uiState.isInspectorOpen)
                                }
                                RibbonGroup("Appearance") {
                                    RibbonActionButton(
                                        if (uiState.isDarkCanvas) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        if (uiState.isDarkCanvas) "Light Mode" else "Dark Mode",
                                        onToggleDarkTheme,
                                        "btn_layout_theme"
                                    )
                                }
                            }

                            RibbonTab.REFERENCES -> {}

                            RibbonTab.REVIEW -> {
                                RibbonGroup("Review & Search") {
                                    RibbonActionButton(Icons.Default.Search, "Find (Ctrl+F)", onFind, "btn_review_find")
                                    RibbonActionButton(Icons.Default.Info, "Metadata & Audit", onToggleInspector, "btn_review_audit")
                                    if (activeFormat == DocumentFormat.PDF) {
                                        RibbonActionButton(Icons.Default.SaveAs, "Flatten Vector", onExportFlattenPdf, "btn_review_flatten")
                                    }
                                }
                            }

                            RibbonTab.VIEW -> {
                                RibbonGroup("Zoom") {
                                    RibbonActionButton(Icons.Default.ZoomOut, "Zoom Out", { onZoomDelta(-0.25f) }, "btn_view_zoom_out")
                                    RibbonActionButton(Icons.Default.ZoomIn, "Zoom In", { onZoomDelta(0.25f) }, "btn_view_zoom_in")
                                }
                                RibbonGroup("Accessibility") {
                                    RibbonActionButton(Icons.Default.Keyboard, "Shortcuts", onShowShortcuts, "btn_view_shortcuts")
                                }
                            }

                            RibbonTab.PDF_ANNOTATE -> {
                                RibbonGroup("Tools") {
                                    RibbonActionButton(
                                        Icons.Default.PanTool,
                                        "Browse",
                                        { onSelectPdfTool(PdfEditorTool.BROWSE) },
                                        "btn_tool_browse",
                                        selected = uiState.activePdfTool == PdfEditorTool.BROWSE
                                    )
                                    RibbonActionButton(
                                        Icons.Default.SelectAll,
                                        "Select Text",
                                        { onSelectPdfTool(PdfEditorTool.SELECT_TEXT) },
                                        "btn_tool_select",
                                        selected = uiState.activePdfTool == PdfEditorTool.SELECT_TEXT
                                    )
                                    RibbonActionButton(
                                        Icons.Default.Highlight,
                                        "Highlight",
                                        { onSelectPdfTool(PdfEditorTool.HIGHLIGHT) },
                                        "btn_tool_highlight",
                                        selected = uiState.activePdfTool == PdfEditorTool.HIGHLIGHT
                                    )
                                    RibbonActionButton(
                                        Icons.Default.Brush,
                                        "Stylus Ink",
                                        { onSelectPdfTool(PdfEditorTool.INK_PEN) },
                                        "btn_tool_ink",
                                        selected = uiState.activePdfTool == PdfEditorTool.INK_PEN
                                    )
                                    RibbonActionButton(
                                        Icons.Default.AutoFixNormal,
                                        "Eraser",
                                        { onSelectPdfTool(PdfEditorTool.ERASER) },
                                        "btn_tool_eraser",
                                        selected = uiState.activePdfTool == PdfEditorTool.ERASER
                                    )
                                    RibbonActionButton(
                                        Icons.Default.TextFields,
                                        "Text Box",
                                        { onSelectPdfTool(PdfEditorTool.TEXT_BOX) },
                                        "btn_tool_textbox",
                                        selected = uiState.activePdfTool == PdfEditorTool.TEXT_BOX
                                    )
                                    RibbonActionButton(
                                        Icons.Default.Draw,
                                        "Signature",
                                        onOpenSignaturePad,
                                        "btn_tool_signature",
                                        selected = uiState.activePdfTool == PdfEditorTool.SIGNATURE
                                    )
                                }

                                RibbonGroup("Ink Color") {
                                    val colors = listOf(
                                        0xFF0F548C to "Cobalt",
                                        0xFFDC2626 to "Crimson",
                                        0xFF15803D to "Emerald",
                                        0xFF0F172A to "Black"
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        colors.forEach { (argb, name) ->
                                            val isSelected = uiState.activeInkColorArgb == argb
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(argb))
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { onSelectInkColor(argb) }
                                                    .testTag("ink_color_$name")
                                            )
                                        }
                                    }
                                }

                                RibbonGroup("Stroke") {
                                    val widths = listOf(0.0025f to "Fine", 0.0042f to "Med", 0.0075f to "Bold")
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        widths.forEach { (w, label) ->
                                            val active = kotlin.math.abs(uiState.activeStrokeWidthNormalized - w) < 0.0008f
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .clickable { onSelectStrokeWidth(w) }
                                                    .padding(2.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (active) FluentCobalt else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                RibbonGroup("Export") {
                                    RibbonActionButton(
                                        Icons.Default.SaveAs,
                                        "Flatten & Export",
                                        onExportFlattenPdf,
                                        "btn_flatten_export"
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun RibbonGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.border(
            0.75.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(10.dp)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                content()
            }
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun RibbonActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    selected: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
