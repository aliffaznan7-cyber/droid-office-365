package com.example.ui.xlsx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ooxml.SpreadsheetFormulaEngine
import com.example.domain.model.XlsxWorkbookModel
import com.example.ui.theme.FormatXlsxColor
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun XlsxSpreadsheetScreen(
    model: XlsxWorkbookModel?,
    selectedCell: Pair<Int, Int>,
    formulaBarInput: String,
    onSelectCell: (Int, Int) -> Unit,
    onFormulaInputChange: (String) -> Unit,
    onCommitFormula: () -> Unit,
    onSelectSheet: (Int) -> Unit,
    onAddSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (model == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading SpreadsheetML workbook...", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val sheet = model.worksheets.getOrNull(model.activeSheetIndex) ?: return
    val cellRefLabel = "${SpreadsheetFormulaEngine.colIndexToName(selectedCell.second)}${selectedCell.first + 1}"
    val horizontalScroll = rememberScrollState()
    val cellWidth = 136.dp
    val rowHeaderWidth = 52.dp
    val rowHeight = 36.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Formula Bar (fx)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.width(68.dp)
                ) {
                    Text(
                        text = cellRefLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = "Formula",
                    tint = FormatXlsxColor,
                    modifier = Modifier.size(20.dp)
                )
                OutlinedTextField(
                    value = formulaBarInput,
                    onValueChange = onFormulaInputChange,
                    placeholder = { Text("Enter value or =SUM(B2:B4)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitFormula() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("xlsx_formula_bar")
                )
                IconButton(
                    onClick = onCommitFormula,
                    modifier = Modifier.testTag("xlsx_commit_cell_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Apply cell edit", tint = FormatXlsxColor)
                }
            }
        }

        // Virtualized 2D Spreadsheet Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Sticky Column Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .width(rowHeaderWidth)
                        .height(32.dp)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline)
                )
                Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                    for (col in 0 until sheet.maxCol) {
                        val isColSelected = selectedCell.second == col
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(cellWidth)
                                .height(32.dp)
                                .background(
                                    if (isColSelected) FormatXlsxColor.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = SpreadsheetFormulaEngine.colIndexToName(col),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isColSelected) FormatXlsxColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Virtualized Rows
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("xlsx_grid_rows")
            ) {
                items(sheet.maxRow, key = { it }) { row ->
                    val isRowSelected = selectedCell.first == row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Row index header
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(rowHeaderWidth)
                                .height(rowHeight)
                                .background(
                                    if (isRowSelected) FormatXlsxColor.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "${row + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRowSelected) FormatXlsxColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Horizontal scrollable data cells
                        Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                            for (col in 0 until sheet.maxCol) {
                                val cell = sheet.cells[row to col]
                                val isSelected = selectedCell.first == row && selectedCell.second == col
                                val isNumeric = cell?.cachedValue?.toDoubleOrNull() != null
                                Box(
                                    contentAlignment = if (isNumeric) Alignment.CenterEnd else Alignment.CenterStart,
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(rowHeight)
                                        .background(
                                            when {
                                                isSelected -> FormatXlsxColor.copy(alpha = 0.12f)
                                                row == 0 && cell != null -> MaterialTheme.colorScheme.surfaceContainerLow
                                                else -> MaterialTheme.colorScheme.surfaceContainerLowest
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) FormatXlsxColor else MaterialTheme.colorScheme.outlineVariant
                                        )
                                        .clickable { onSelectCell(row, col) }
                                        .padding(horizontal = 8.dp)
                                        .testTag("xlsx_cell_${row}_$col")
                                ) {
                                    Text(
                                        text = cell?.displayValue ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = if (cell?.isBold == true || row == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (!cell?.formula.isNullOrBlank()) FormatXlsxColor else MaterialTheme.colorScheme.onSurface,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Bottom Worksheet Tabs Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            model.worksheets.forEachIndexed { idx, ws ->
                val active = idx == model.activeSheetIndex
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                    modifier = Modifier
                        .border(
                            width = if (active) 1.dp else 0.dp,
                            color = if (active) FormatXlsxColor else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectSheet(idx) }
                        .testTag("xlsx_sheet_tab_$idx")
                ) {
                    Text(
                        text = ws.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) FormatXlsxColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            TextButton(
                onClick = onAddSheet,
                modifier = Modifier.testTag("xlsx_add_sheet_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sheet")
            }
        }
    }
}
