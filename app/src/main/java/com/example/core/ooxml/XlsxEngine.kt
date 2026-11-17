package com.example.core.ooxml

import android.util.Xml
import com.example.domain.model.CoreDocumentProperties
import com.example.domain.model.XlsxCell
import com.example.domain.model.XlsxWorkbookModel
import com.example.domain.model.XlsxWorksheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Streaming SpreadsheetML (.xlsx) parser & OPC writer (Part 1 Section 4.4 & Section 5.9).
 */
object XlsxEngine {

    suspend fun loadWorkbook(documentId: String, file: File): XlsxWorkbookModel =
        withContext(Dispatchers.Default) {
            val parts = OpcPackageManager.readPackageParts(file)
            val coreProps = OpcPackageManager.parseCoreProperties(parts["docProps/core.xml"])
            val sharedStrings = parseSharedStrings(parts["xl/sharedStrings.xml"])
            val styleFormats = parseStyleNumberFormats(parts["xl/styles.xml"])
            val sheetDefs = parseWorkbookSheets(parts["xl/workbook.xml"])

            val worksheets = sheetDefs.mapIndexed { idx, (sheetId, name) ->
                val sheetBytes = parts["xl/worksheets/sheet${idx + 1}.xml"]
                val rawSheet = if (sheetBytes != null) {
                    parseWorksheetStream(sheetId, name, sheetBytes, sharedStrings, styleFormats)
                } else {
                    XlsxWorksheet(sheetId = sheetId, name = name)
                }
                SpreadsheetFormulaEngine.recalculateWorksheet(rawSheet)
            }.ifEmpty {
                listOf(XlsxWorksheet(sheetId = 1, name = "Sheet1"))
            }

            XlsxWorkbookModel(
                documentId = documentId,
                coreProperties = coreProps,
                worksheets = worksheets
            )
        }

    private fun parseSharedStrings(xmlBytes: ByteArray?): List<String> {
        if (xmlBytes == null) return emptyList()
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())

        var inSi = false
        var inT = false
        var sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inSi = true
                        sb = StringBuilder()
                    }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) sb.append(parser.text ?: "")
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> {
                        list.add(sb.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseStyleNumberFormats(xmlBytes: ByteArray?): Map<Int, Pair<String, Boolean>> {
        if (xmlBytes == null) return emptyMap()
        val numFmts = mutableMapOf<Int, String>(
            0 to "General",
            2 to "#,##0.00",
            4 to "#,##0.00",
            9 to "0%",
            10 to "0.00%",
            164 to "RM #,##0.00"
        )
        val xfs = mutableMapOf<Int, Pair<String, Boolean>>()
        val boldFontIds = mutableSetOf<Int>()

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())

            var inFonts = false
            var fontIndex = -1
            var fontBold = false
            var inCellXfs = false
            var xfIndex = 0

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> when (tag) {
                        "numFmt" -> {
                            val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                            val code = parser.getAttributeValue(null, "formatCode")
                            if (id != null && code != null) numFmts[id] = code
                        }
                        "fonts" -> {
                            inFonts = true
                            fontIndex = -1
                        }
                        "font" -> if (inFonts) {
                            fontIndex++
                            fontBold = false
                        }
                        "b" -> if (inFonts) fontBold = true
                        "cellXfs" -> {
                            inCellXfs = true
                            xfIndex = 0
                        }
                        "xf" -> if (inCellXfs) {
                            val numFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                            val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                            val format = numFmts[numFmtId] ?: "General"
                            xfs[xfIndex] = format to boldFontIds.contains(fontId)
                            xfIndex++
                        }
                    }
                    XmlPullParser.END_TAG -> when (tag) {
                        "font" -> if (inFonts && fontBold) boldFontIds.add(fontIndex)
                        "fonts" -> inFonts = false
                        "cellXfs" -> inCellXfs = false
                    }
                }
                event = parser.next()
            }
        }
        return xfs
    }

    private fun parseWorkbookSheets(xmlBytes: ByteArray?): List<Pair<Int, String>> {
        if (xmlBytes == null) return emptyList()
        val sheets = mutableListOf<Pair<Int, String>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val id = parser.getAttributeValue(null, "sheetId")?.toIntOrNull() ?: (sheets.size + 1)
                sheets.add(id to name)
            }
            event = parser.next()
        }
        return sheets
    }

    private fun parseWorksheetStream(
        sheetId: Int,
        name: String,
        xmlBytes: ByteArray,
        sharedStrings: List<String>,
        styles: Map<Int, Pair<String, Boolean>>
    ): XlsxWorksheet {
        val cells = linkedMapOf<Pair<Int, Int>, XlsxCell>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())

        var currentRef: Pair<Int, Int>? = null
        var cellType = ""
        var styleIndex = 0
        var inFormula = false
        var inValue = false
        var inInlineText = false
        var formulaSb = StringBuilder()
        var valueSb = StringBuilder()
        var maxRow = 35
        var maxCol = 12

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        currentRef = SpreadsheetFormulaEngine.parseCellRef(ref)
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        styleIndex = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                        formulaSb = StringBuilder()
                        valueSb = StringBuilder()
                    }
                    "f" -> if (currentRef != null) inFormula = true
                    "v" -> if (currentRef != null) inValue = true
                    "t" -> if (currentRef != null && cellType == "inlineStr") inInlineText = true
                }

                XmlPullParser.TEXT -> {
                    val txt = parser.text ?: ""
                    if (inFormula) formulaSb.append(txt)
                    else if (inValue || inInlineText) valueSb.append(txt)
                }

                XmlPullParser.END_TAG -> when (tag) {
                    "f" -> inFormula = false
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val coord = currentRef
                        if (coord != null) {
                            val (fmt, isBold) = styles[styleIndex] ?: ("General" to false)
                            val formula = formulaSb.toString().trim().takeIf { it.isNotEmpty() }
                            val rawVal = valueSb.toString()
                            val resolvedValue = when (cellType) {
                                "s" -> rawVal.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: rawVal
                                "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                                else -> rawVal
                            }
                            val rawInput = if (formula != null) "=$formula" else resolvedValue
                            cells[coord] = XlsxCell(
                                row = coord.first,
                                col = coord.second,
                                rawInput = rawInput,
                                formula = formula,
                                cachedValue = resolvedValue,
                                displayValue = SpreadsheetFormulaEngine.formatDisplayValue(resolvedValue, fmt),
                                numberFormat = fmt,
                                isBold = isBold
                            )
                            maxRow = maxOf(maxRow, coord.first + 8)
                            maxCol = maxOf(maxCol, coord.second + 4)
                        }
                        currentRef = null
                    }
                }
            }
            event = parser.next()
        }

        return XlsxWorksheet(
            sheetId = sheetId,
            name = name,
            cells = cells,
            maxRow = maxRow,
            maxCol = maxCol
        )
    }

    suspend fun saveWorkbook(model: XlsxWorkbookModel, destination: File) =
        withContext(Dispatchers.Default) {
            val sharedStrings = mutableListOf<String>()
            val sharedIndex = HashMap<String, Int>()

            fun internString(text: String): Int {
                return sharedIndex.getOrPut(text) {
                    sharedStrings.add(text)
                    sharedStrings.lastIndex
                }
            }

            val parts = LinkedHashMap<String, ByteArray>()
            parts["[Content_Types].xml"] = buildContentTypesXml(model.worksheets.size)
            parts["_rels/.rels"] = defaultPackageRelsXml()
            parts["docProps/core.xml"] = OpcPackageManager.buildCorePropertiesXml(model.coreProperties)
            parts["xl/workbook.xml"] = buildWorkbookXml(model.worksheets)
            parts["xl/_rels/workbook.xml.rels"] = buildWorkbookRelsXml(model.worksheets.size)
            parts["xl/styles.xml"] = defaultStylesXml()

            model.worksheets.forEachIndexed { idx, rawSheet ->
                val sheet = SpreadsheetFormulaEngine.recalculateWorksheet(rawSheet)
                parts["xl/worksheets/sheet${idx + 1}.xml"] = buildWorksheetXml(sheet, ::internString)
            }
            parts["xl/sharedStrings.xml"] = buildSharedStringsXml(sharedStrings)

            OpcPackageManager.writePackageParts(destination, parts)
        }

    private fun buildWorksheetXml(
        sheet: XlsxWorksheet,
        internString: (String) -> Int
    ): ByteArray {
        val byRow = sheet.cells.values.groupBy { it.row }.toSortedMap()
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            byRow.forEach { (rowIdx, rowCells) ->
                append("""<row r="${rowIdx + 1}">""")
                rowCells.sortedBy { it.col }.forEach { cell ->
                    val ref = "${SpreadsheetFormulaEngine.colIndexToName(cell.col)}${rowIdx + 1}"
                    val styleIdx = when {
                        cell.numberFormat.contains("RM", ignoreCase = true) && cell.isBold -> 3
                        cell.numberFormat.contains("RM", ignoreCase = true) -> 2
                        cell.isBold -> 1
                        else -> 0
                    }
                    if (!cell.formula.isNullOrBlank()) {
                        append("""<c r="$ref" s="$styleIdx">""")
                        append("<f>${OpcPackageManager.escapeXml(cell.formula.removePrefix("="))}</f>")
                        append("<v>${OpcPackageManager.escapeXml(cell.cachedValue)}</v>")
                        append("</c>")
                    } else {
                        val num = cell.cachedValue.toDoubleOrNull()
                        if (num != null) {
                            append("""<c r="$ref" s="$styleIdx"><v>${OpcPackageManager.escapeXml(cell.cachedValue)}</v></c>""")
                        } else if (cell.cachedValue.isNotEmpty()) {
                            val sIdx = internString(cell.cachedValue)
                            append("""<c r="$ref" t="s" s="$styleIdx"><v>$sIdx</v></c>""")
                        }
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildSharedStringsXml(strings: List<String>): ByteArray {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
            strings.forEach { str ->
                append("""<si><t xml:space="preserve">${OpcPackageManager.escapeXml(str)}</t></si>""")
            }
            append("</sst>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildWorkbookXml(sheets: List<XlsxWorksheet>): ByteArray {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            sheets.forEachIndexed { idx, sheet ->
                append("""<sheet name="${OpcPackageManager.escapeXml(sheet.name)}" sheetId="${sheet.sheetId}" r:id="rId${idx + 1}"/>""")
            }
            append("</sheets></workbook>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): ByteArray {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            for (i in 1..sheetCount) {
                append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
            }
            append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
            append("""<Relationship Id="rId${sheetCount + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("</Relationships>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildContentTypesXml(sheetCount: Int): ByteArray {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
            append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
            for (i in 1..sheetCount) {
                append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("</Types>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun defaultPackageRelsXml(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
        </Relationships>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun defaultStylesXml(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;RM &quot;#,##0.00"/></numFmts>
            <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><sz val="11"/><name val="Calibri"/></font>
            </fonts>
            <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
            <borders count="1"><border/></borders>
            <cellXfs count="4">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                <xf numFmtId="0" fontId="1" fillId="0" borderId="0" applyFont="1"/>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0" applyNumberFormat="1"/>
                <xf numFmtId="164" fontId="1" fillId="0" borderId="0" applyFont="1" applyNumberFormat="1"/>
            </cellXfs>
        </styleSheet>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
}
