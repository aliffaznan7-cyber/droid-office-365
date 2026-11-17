package com.example.core.ooxml

import android.util.Xml
import com.example.data.local.DocuDeskConverters
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxCitation
import com.example.domain.model.DocxComment
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxMargins
import com.example.domain.model.DocxMarginsPreset
import com.example.domain.model.DocxNote
import com.example.domain.model.DocxNoteKind
import com.example.domain.model.DocxOrientation
import com.example.domain.model.DocxPaperSize
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxScriptMode
import com.example.domain.model.DocxTextRun
import com.example.domain.model.DocxTrackChangeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-fidelity WordprocessingML (.docx) parser & OPC serializer implementing:
 * - Styles (Normal, Title, Subtitle, Heading 1-6, Quote, Bulleted/Numbered/Multilevel Lists)
 * - Runs (rFonts, sz, b, i, u, strike, vertAlign, color, highlight, hyperlink, w:ins/w:del)
 * - Paragraphs (jc, spacing line/before/after, ind left/firstLine, comment/footnote/endnote refs)
 * - Blocks (Paragraph, Table, PageBreak, Drawing/Image media, Stylus Sketch, TextBox, OMML Equation, TOC)
 * - Page Setup (Margins, Portrait/Landscape, A4/Letter/Legal, Columns, Header & Footer)
 * - Separate OPC XML Parts (comments.xml, footnotes.xml, endnotes.xml, numbering.xml, customXml extras)
 */
object DocxEngine {

    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    suspend fun loadDocument(
        documentId: String,
        file: File,
        mediaExtractDir: File? = null
    ): DocxDocumentModel = withContext(Dispatchers.Default) {
        val parts = OpcPackageManager.readPackageParts(file)
        val coreProps = OpcPackageManager.parseCoreProperties(parts["docProps/core.xml"])
        val docRels = parseRelationships(parts["word/_rels/document.xml.rels"])

        // Extract embedded media bytes so ImageBlock renders directly from local disk
        val mediaPaths = mutableMapOf<String, String>()
        val targetMediaDir = mediaExtractDir ?: File(file.parentFile, "media_${documentId.take(8)}")
        parts.forEach { (partName, bytes) ->
            if (partName.startsWith("word/media/")) {
                targetMediaDir.mkdirs()
                val shortName = partName.substringAfterLast('/')
                val outMedia = File(targetMediaDir, shortName)
                outMedia.writeBytes(bytes)
                mediaPaths[partName] = outMedia.absolutePath
            }
        }

        val comments = parseCommentsXml(parts["word/comments.xml"])
        val footnotes = parseNotesXml(parts["word/footnotes.xml"], DocxNoteKind.FOOTNOTE)
        val endnotes = parseNotesXml(parts["word/endnotes.xml"], DocxNoteKind.ENDNOTE)
        val headerText = parseSimpleHeaderFooterXml(parts["word/header1.xml"])
        val footerInfo = parseFooterXml(parts["word/footer1.xml"])
        val extras = parseCustomExtrasXml(parts["customXml/docudesk_extras.xml"])

        val documentXml = parts["word/document.xml"]
            ?: return@withContext DocxDocumentModel(documentId = documentId, coreProperties = coreProps)

        val parsedDoc = parseDocumentXml(documentXml, docRels, mediaPaths, extras)

        DocxDocumentModel(
            documentId = documentId,
            coreProperties = coreProps,
            blocks = parsedDoc.blocks,
            margins = parsedDoc.margins,
            orientation = parsedDoc.orientation,
            paperSize = parsedDoc.paperSize,
            columnCount = parsedDoc.columnCount,
            headerText = headerText,
            footerText = footerInfo.first,
            showPageNumbers = footerInfo.second,
            comments = comments,
            footnotes = footnotes,
            endnotes = endnotes,
            citations = extras.citations,
            trackChangesEnabled = extras.trackChangesEnabled,
            showMarkup = extras.showMarkup
        )
    }

    private data class ParsedDocumentPayload(
        val blocks: List<DocxBlock>,
        val margins: DocxMargins,
        val orientation: DocxOrientation,
        val paperSize: DocxPaperSize,
        val columnCount: Int
    )

    private data class CustomExtrasPayload(
        val drawingsById: Map<String, DocxBlock.DrawingBlock> = emptyMap(),
        val textBoxesById: Map<String, DocxBlock.TextBoxBlock> = emptyMap(),
        val equationsById: Map<String, DocxBlock.EquationBlock> = emptyMap(),
        val citations: List<DocxCitation> = emptyList(),
        val trackChangesEnabled: Boolean = false,
        val showMarkup: Boolean = true
    )

    fun parseDocumentXml(xmlBytes: ByteArray): List<DocxBlock> {
        return parseDocumentXml(xmlBytes, emptyMap(), emptyMap(), CustomExtrasPayload()).blocks
    }

    private fun parseDocumentXml(
        xmlBytes: ByteArray,
        docRels: Map<String, String>,
        mediaPaths: Map<String, String>,
        extras: CustomExtrasPayload
    ): ParsedDocumentPayload {
        val blocks = mutableListOf<DocxBlock>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())

        var inBody = false
        var inSdt = false
        var sdtIsToc = false

        var inTable = false
        var inTableRow = false
        var inTableCell = false
        var currentTableRows = mutableListOf<List<String>>()
        var currentRowCells = mutableListOf<String>()
        var currentCellText = StringBuilder()

        var inParagraph = false
        var currentStyle = DocxParagraphStyle.NORMAL
        var currentAlign = DocxAlignment.LEFT
        var currentListLevel = 0
        var currentNumId = 0
        var currentLineSpacing = 1.15f
        var currentSpaceBefore = 0f
        var currentSpaceAfter = 8f
        var currentLeftIndent = 0f
        var currentFirstIndent = 0f
        var currentCommentIds = mutableListOf<String>()
        var currentFootnoteIds = mutableListOf<String>()
        var currentEndnoteIds = mutableListOf<String>()
        var currentRuns = mutableListOf<DocxTextRun>()
        var hasPageBreak = false
        var embeddedImageTarget: String? = null
        var inlineMarkerText = StringBuilder()
        var inMathPara = false
        var inMath = false
        var mathText = StringBuilder()

        var activeHyperlinkUrl: String? = null
        var activeTrackType = DocxTrackChangeType.NONE
        var activeTrackAuthor = "DocuDesk User"
        var activeTrackDate = ""

        var inRun = false
        var fontFamily = "Merriweather"
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var scriptMode = DocxScriptMode.NORMAL
        var fontSizeSp = 12f
        var colorHex: String? = null
        var highlightHex: String? = null
        var inText = false
        var runText = StringBuilder()

        var margins = DocxMargins()
        var orientation = DocxOrientation.PORTRAIT
        var paperSize = DocxPaperSize.A4
        var columnCount = 1

        fun attr(name: String): String? =
            parser.getAttributeValue(null, name) ?: parser.getAttributeValue(W_NS, name)

        fun relAttr(name: String): String? =
            parser.getAttributeValue(R_NS, name) ?: parser.getAttributeValue(null, name)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "body" -> inBody = true
                    "sdt" -> if (inBody && !inTable) {
                        inSdt = true
                        sdtIsToc = false
                    }
                    "docPartGallery" -> if (inSdt) {
                        val v = attr("val") ?: ""
                        if (v.contains("Table of Contents", ignoreCase = true)) {
                            sdtIsToc = true
                        }
                    }
                    "oMathPara" -> if (inBody && !inTable) {
                        inMathPara = true
                        inMath = true
                        mathText = StringBuilder()
                    }
                    "oMath" -> if (inBody && !inTable) {
                        inMath = true
                        if (!inMathPara) mathText = StringBuilder()
                    }
                    "tbl" -> if (inBody && !inSdt) {
                        inTable = true
                        currentTableRows = mutableListOf()
                    }
                    "tr" -> if (inTable) {
                        inTableRow = true
                        currentRowCells = mutableListOf()
                    }
                    "tc" -> if (inTableRow) {
                        inTableCell = true
                        currentCellText = StringBuilder()
                    }
                    "p" -> if (inBody && !inTable && !inSdt) {
                        inParagraph = true
                        currentStyle = DocxParagraphStyle.NORMAL
                        currentAlign = DocxAlignment.LEFT
                        currentListLevel = 0
                        currentNumId = 0
                        currentLineSpacing = 1.15f
                        currentSpaceBefore = 0f
                        currentSpaceAfter = 8f
                        currentLeftIndent = 0f
                        currentFirstIndent = 0f
                        currentCommentIds = mutableListOf()
                        currentFootnoteIds = mutableListOf()
                        currentEndnoteIds = mutableListOf()
                        currentRuns = mutableListOf()
                        hasPageBreak = false
                        embeddedImageTarget = null
                        inlineMarkerText = StringBuilder()
                    }
                    "pStyle" -> if (inParagraph) {
                        currentStyle = when (attr("val")?.lowercase()) {
                            "title" -> DocxParagraphStyle.TITLE
                            "subtitle" -> DocxParagraphStyle.SUBTITLE
                            "heading1" -> DocxParagraphStyle.HEADING_1
                            "heading2" -> DocxParagraphStyle.HEADING_2
                            "heading3" -> DocxParagraphStyle.HEADING_3
                            "heading4" -> DocxParagraphStyle.HEADING_4
                            "heading5" -> DocxParagraphStyle.HEADING_5
                            "heading6" -> DocxParagraphStyle.HEADING_6
                            "quote", "intensequote" -> DocxParagraphStyle.QUOTE
                            "listparagraph" -> DocxParagraphStyle.BULLET
                            else -> DocxParagraphStyle.NORMAL
                        }
                    }
                    "ilvl" -> if (inParagraph) {
                        currentListLevel = attr("val")?.toIntOrNull()?.coerceIn(0, 2) ?: 0
                    }
                    "numId" -> if (inParagraph) {
                        currentNumId = attr("val")?.toIntOrNull() ?: 0
                        currentStyle = when (currentNumId) {
                            2 -> DocxParagraphStyle.NUMBERED
                            3 -> DocxParagraphStyle.MULTILEVEL
                            1 -> DocxParagraphStyle.BULLET
                            else -> currentStyle
                        }
                    }
                    "spacing" -> if (inParagraph) {
                        attr("line")?.toFloatOrNull()?.let { lineTwips ->
                            currentLineSpacing = (lineTwips / 240f).coerceIn(1.0f, 2.5f)
                        }
                        attr("before")?.toFloatOrNull()?.let { currentSpaceBefore = it / 20f }
                        attr("after")?.toFloatOrNull()?.let { currentSpaceAfter = it / 20f }
                    }
                    "ind" -> if (inParagraph) {
                        attr("left")?.toFloatOrNull()?.let { currentLeftIndent = it / 20f }
                        attr("firstLine")?.toFloatOrNull()?.let { currentFirstIndent = it / 20f }
                    }
                    "jc" -> if (inParagraph) {
                        currentAlign = when (attr("val")?.lowercase()) {
                            "center" -> DocxAlignment.CENTER
                            "right", "end" -> DocxAlignment.RIGHT
                            "both", "distribute" -> DocxAlignment.JUSTIFY
                            else -> DocxAlignment.LEFT
                        }
                    }
                    "commentRangeStart" -> if (inParagraph) {
                        attr("id")?.let { currentCommentIds.add(it) }
                    }
                    "footnoteReference" -> if (inParagraph) {
                        attr("id")?.let { if (it != "-1" && it != "0") currentFootnoteIds.add(it) }
                    }
                    "endnoteReference" -> if (inParagraph) {
                        attr("id")?.let { if (it != "-1" && it != "0") currentEndnoteIds.add(it) }
                    }
                    "hyperlink" -> if (inParagraph) {
                        val rId = relAttr("id")
                        activeHyperlinkUrl = rId?.let { docRels[it] }
                    }
                    "ins" -> if (inParagraph) {
                        activeTrackType = DocxTrackChangeType.INSERTION
                        activeTrackAuthor = attr("author") ?: "Reviewer"
                        activeTrackDate = attr("date") ?: ""
                    }
                    "del" -> if (inParagraph) {
                        activeTrackType = DocxTrackChangeType.DELETION
                        activeTrackAuthor = attr("author") ?: "Reviewer"
                        activeTrackDate = attr("date") ?: ""
                    }
                    "r" -> if (inParagraph && !inMath) {
                        inRun = true
                        fontFamily = "Merriweather"
                        bold = false
                        italic = false
                        underline = false
                        strike = false
                        scriptMode = DocxScriptMode.NORMAL
                        fontSizeSp = defaultFontSizeForStyle(currentStyle)
                        colorHex = null
                        highlightHex = null
                        runText = StringBuilder()
                    }
                    "rFonts" -> if (inRun) {
                        val ascii = attr("ascii") ?: attr("hAnsi")
                        if (!ascii.isNullOrBlank()) fontFamily = ascii
                    }
                    "b" -> if (inRun) {
                        val v = attr("val")
                        bold = v != "0" && v != "false"
                    }
                    "i" -> if (inRun) {
                        val v = attr("val")
                        italic = v != "0" && v != "false"
                    }
                    "u" -> if (inRun) {
                        underline = attr("val") != "none"
                    }
                    "strike" -> if (inRun) {
                        val v = attr("val")
                        strike = v != "0" && v != "false"
                    }
                    "vertAlign" -> if (inRun) {
                        scriptMode = when (attr("val")?.lowercase()) {
                            "superscript" -> DocxScriptMode.SUPERSCRIPT
                            "subscript" -> DocxScriptMode.SUBSCRIPT
                            else -> DocxScriptMode.NORMAL
                        }
                    }
                    "sz" -> if (inRun) {
                        attr("val")?.toFloatOrNull()?.let { halfPts ->
                            if (halfPts > 0f) fontSizeSp = halfPts / 2f
                        }
                    }
                    "color" -> if (inRun) {
                        val hex = attr("val")
                        if (!hex.isNullOrBlank() && hex != "auto") colorHex = hex
                    }
                    "highlight" -> if (inRun) {
                        highlightHex = mapWordHighlightToHex(attr("val"))
                    }
                    "blip" -> if (inParagraph) {
                        val embedId = relAttr("embed")
                        val target = embedId?.let { docRels[it] }
                        if (target != null) {
                            val normalized = if (target.startsWith("word/")) target else "word/$target"
                            embeddedImageTarget = normalized
                        }
                    }
                    "t", "delText" -> if (inRun || inTableCell || inMath) {
                        inText = true
                    }
                    "br" -> if (inRun) {
                        if (attr("type") == "page") {
                            hasPageBreak = true
                        } else {
                            runText.append("\n")
                        }
                    }
                    "tab" -> if (inRun) runText.append("    ")
                    "pgSz" -> {
                        val wTwips = attr("w")?.toFloatOrNull() ?: 11906f
                        val hTwips = attr("h")?.toFloatOrNull() ?: 16838f
                        val orient = attr("orient")?.lowercase()
                        orientation = if (orient == "landscape" || wTwips > hTwips) {
                            DocxOrientation.LANDSCAPE
                        } else {
                            DocxOrientation.PORTRAIT
                        }
                        val minIn = minOf(wTwips, hTwips) / 1440f
                        val maxIn = maxOf(wTwips, hTwips) / 1440f
                        paperSize = when {
                            maxIn > 12.8f -> DocxPaperSize.LEGAL
                            abs(minIn - 8.5f) < 0.15f -> DocxPaperSize.LETTER
                            else -> DocxPaperSize.A4
                        }
                    }
                    "pgMar" -> {
                        val top = (attr("top")?.toFloatOrNull() ?: 1440f) / 1440f
                        val bottom = (attr("bottom")?.toFloatOrNull() ?: 1440f) / 1440f
                        val left = (attr("left")?.toFloatOrNull() ?: 1440f) / 1440f
                        val right = (attr("right")?.toFloatOrNull() ?: 1440f) / 1440f
                        val preset = matchMarginsPreset(top, bottom, left, right)
                        margins = DocxMargins(preset, top, bottom, left, right)
                    }
                    "cols" -> {
                        columnCount = attr("num")?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inText) {
                        val txt = parser.text ?: ""
                        if (inMath) mathText.append(txt)
                        else if (inTableCell) currentCellText.append(txt)
                        else if (inRun) runText.append(txt)
                    }
                }

                XmlPullParser.END_TAG -> when (tag) {
                    "t", "delText" -> inText = false
                    "oMathPara" -> if (inMathPara) {
                        inMathPara = false
                        inMath = false
                        val expr = mathText.toString().trim()
                        if (expr.isNotEmpty()) {
                            blocks.add(
                                DocxBlock.EquationBlock(
                                    id = UUID.randomUUID().toString(),
                                    expression = expr,
                                    displayUnicode = expr
                                )
                            )
                        }
                    }
                    "oMath" -> if (inBody && !inSdt) {
                        inMath = false
                        if (!inMathPara) {
                            val expr = mathText.toString().trim()
                            if (expr.isNotEmpty()) {
                                blocks.add(
                                    DocxBlock.EquationBlock(
                                        id = UUID.randomUUID().toString(),
                                        expression = expr,
                                        displayUnicode = expr
                                    )
                                )
                            }
                        }
                    }
                    "sdt" -> if (inSdt) {
                        if (sdtIsToc) {
                            blocks.add(DocxBlock.TableOfContentsBlock(id = UUID.randomUUID().toString()))
                        }
                        inSdt = false
                        sdtIsToc = false
                    }
                    "hyperlink" -> activeHyperlinkUrl = null
                    "ins", "del" -> {
                        activeTrackType = DocxTrackChangeType.NONE
                    }
                    "r" -> if (inRun) {
                        val text = runText.toString()
                        if (text.startsWith("[[DOCUDESK_DRAWING:") && text.endsWith("]]")) {
                            inlineMarkerText.append(text)
                        } else if (text.startsWith("[[DOCUDESK_TEXTBOX:") && text.endsWith("]]")) {
                            inlineMarkerText.append(text)
                        } else if (text.startsWith("[[DOCUDESK_EQ:") && text.endsWith("]]")) {
                            inlineMarkerText.append(text)
                        } else if (text.isNotEmpty()) {
                            currentRuns.add(
                                DocxTextRun(
                                    id = UUID.randomUUID().toString(),
                                    text = text,
                                    fontFamily = fontFamily,
                                    bold = bold,
                                    italic = italic,
                                    underline = underline,
                                    strikethrough = strike,
                                    scriptMode = scriptMode,
                                    fontSizeSp = fontSizeSp,
                                    colorHex = colorHex,
                                    highlightColorHex = highlightHex,
                                    hyperlinkUrl = activeHyperlinkUrl,
                                    trackChangeType = activeTrackType,
                                    trackAuthor = activeTrackAuthor,
                                    trackDateIso = activeTrackDate
                                )
                            )
                        }
                        inRun = false
                    }
                    "p" -> if (inParagraph) {
                        val marker = inlineMarkerText.toString()
                        when {
                            hasPageBreak && currentRuns.isEmpty() -> {
                                blocks.add(DocxBlock.PageBreak(UUID.randomUUID().toString()))
                            }
                            embeddedImageTarget != null -> {
                                val path = mediaPaths[embeddedImageTarget] ?: ""
                                val fileName = embeddedImageTarget!!.substringAfterLast('/')
                                val caption = currentRuns.joinToString("") { it.text }
                                blocks.add(
                                    DocxBlock.ImageBlock(
                                        id = UUID.randomUUID().toString(),
                                        mediaFileName = fileName,
                                        localFilePath = path,
                                        caption = caption
                                    )
                                )
                            }
                            marker.startsWith("[[DOCUDESK_DRAWING:") -> {
                                val id = marker.removePrefix("[[DOCUDESK_DRAWING:").removeSuffix("]]")
                                extras.drawingsById[id]?.let { blocks.add(it) }
                            }
                            marker.startsWith("[[DOCUDESK_TEXTBOX:") -> {
                                val id = marker.removePrefix("[[DOCUDESK_TEXTBOX:").removeSuffix("]]")
                                extras.textBoxesById[id]?.let { blocks.add(it) }
                            }
                            marker.startsWith("[[DOCUDESK_EQ:") -> {
                                val id = marker.removePrefix("[[DOCUDESK_EQ:").removeSuffix("]]")
                                extras.equationsById[id]?.let { blocks.add(it) }
                            }
                            else -> {
                                blocks.add(
                                    DocxBlock.Paragraph(
                                        id = UUID.randomUUID().toString(),
                                        style = currentStyle,
                                        alignment = currentAlign,
                                        listLevel = currentListLevel,
                                        lineSpacingMultiplier = currentLineSpacing,
                                        spaceBeforePt = currentSpaceBefore,
                                        spaceAfterPt = currentSpaceAfter,
                                        leftIndentPt = currentLeftIndent,
                                        firstLineIndentPt = currentFirstIndent,
                                        runs = if (currentRuns.isEmpty()) {
                                            listOf(DocxTextRun(UUID.randomUUID().toString(), ""))
                                        } else currentRuns,
                                        commentIds = currentCommentIds.toList(),
                                        footnoteIds = currentFootnoteIds.toList(),
                                        endnoteIds = currentEndnoteIds.toList()
                                    )
                                )
                            }
                        }
                        inParagraph = false
                    } else if (inTableCell && currentCellText.isNotEmpty()) {
                        currentCellText.append(" ")
                    }
                    "tc" -> if (inTableCell) {
                        currentRowCells.add(currentCellText.toString().trim())
                        inTableCell = false
                    }
                    "tr" -> if (inTableRow) {
                        currentTableRows.add(currentRowCells.toList())
                        inTableRow = false
                    }
                    "tbl" -> if (inTable) {
                        blocks.add(
                            DocxBlock.Table(
                                id = UUID.randomUUID().toString(),
                                rows = currentTableRows.toList()
                            )
                        )
                        inTable = false
                    }
                    "body" -> inBody = false
                }
            }
            event = parser.next()
        }

        return ParsedDocumentPayload(
            blocks = blocks,
            margins = margins,
            orientation = orientation,
            paperSize = paperSize,
            columnCount = columnCount
        )
    }

    suspend fun saveDocument(model: DocxDocumentModel, destination: File) =
        withContext(Dispatchers.Default) {
            val existingParts = if (destination.exists()) {
                runCatching { OpcPackageManager.readPackageParts(destination) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

            val parts = LinkedHashMap<String, ByteArray>(existingParts)
            val hyperlinkRels = linkedMapOf<String, String>() // url -> rId
            val imageRels = linkedMapOf<String, String>() // mediaFileName -> rId
            var relCounter = 10

            model.blocks.forEach { block ->
                when (block) {
                    is DocxBlock.Paragraph -> {
                        block.runs.forEach { run ->
                            val url = run.hyperlinkUrl
                            if (!url.isNullOrBlank() && !hyperlinkRels.containsKey(url)) {
                                hyperlinkRels[url] = "rId${relCounter++}"
                            }
                        }
                    }
                    is DocxBlock.ImageBlock -> {
                        val file = File(block.localFilePath)
                        if (file.exists()) {
                            parts["word/media/${block.mediaFileName}"] = file.readBytes()
                        }
                        if (!imageRels.containsKey(block.mediaFileName)) {
                            imageRels[block.mediaFileName] = "rId${relCounter++}"
                        }
                    }
                    else -> {}
                }
            }

            parts["[Content_Types].xml"] = buildContentTypesXml(
                hasImages = imageRels.isNotEmpty()
            )
            parts["_rels/.rels"] = defaultPackageRelsXml()
            parts["word/_rels/document.xml.rels"] = buildDocumentRelsXml(hyperlinkRels, imageRels)
            parts["word/styles.xml"] = defaultStylesXml()
            parts["word/numbering.xml"] = defaultNumberingXml()
            parts["word/header1.xml"] = buildHeaderXml(model.headerText)
            parts["word/footer1.xml"] = buildFooterXml(model.footerText, model.showPageNumbers)
            parts["word/comments.xml"] = buildCommentsXml(model.comments)
            parts["word/footnotes.xml"] = buildNotesXml(model.footnotes, isFootnote = true)
            parts["word/endnotes.xml"] = buildNotesXml(model.endnotes, isFootnote = false)
            parts["customXml/docudesk_extras.xml"] = buildCustomExtrasXml(model)
            parts["docProps/core.xml"] = OpcPackageManager.buildCorePropertiesXml(model.coreProperties)
            parts["word/document.xml"] = buildDocumentXml(model, hyperlinkRels, imageRels)

            OpcPackageManager.writePackageParts(destination, parts)
        }

    private fun buildDocumentXml(
        model: DocxDocumentModel,
        hyperlinkRels: Map<String, String>,
        imageRels: Map<String, String>
    ): ByteArray {
        val rawW = (model.paperSize.widthIn * 1440f).roundToInt()
        val rawH = (model.paperSize.heightIn * 1440f).roundToInt()
        val pgW = if (model.orientation == DocxOrientation.LANDSCAPE) rawH else rawW
        val pgH = if (model.orientation == DocxOrientation.LANDSCAPE) rawW else rawH
        val orientAttr = if (model.orientation == DocxOrientation.LANDSCAPE) """ w:orient="landscape"""" else ""

        val mTop = (model.margins.topInches * 1440f).roundToInt().coerceIn(360, 2880)
        val mBottom = (model.margins.bottomInches * 1440f).roundToInt().coerceIn(360, 2880)
        val mLeft = (model.margins.leftInches * 1440f).roundToInt().coerceIn(360, 2880)
        val mRight = (model.margins.rightInches * 1440f).roundToInt().coerceIn(360, 2880)

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append(
                """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                    """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
                    """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                    """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                    """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" """ +
                    """xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">"""
            )
            append("<w:body>")
            var docImageId = 1

            model.blocks.forEach { block ->
                when (block) {
                    is DocxBlock.Paragraph -> {
                        append("<w:p><w:pPr>")
                        val styleId = styleToWordStyleId(block.style)
                        append("""<w:pStyle w:val="$styleId"/>""")
                        if (block.style == DocxParagraphStyle.BULLET ||
                            block.style == DocxParagraphStyle.NUMBERED ||
                            block.style == DocxParagraphStyle.MULTILEVEL
                        ) {
                            val numId = when (block.style) {
                                DocxParagraphStyle.BULLET -> 1
                                DocxParagraphStyle.NUMBERED -> 2
                                DocxParagraphStyle.MULTILEVEL -> 3
                                else -> 1
                            }
                            append("""<w:numPr><w:ilvl w:val="${block.listLevel.coerceIn(0, 2)}"/><w:numId w:val="$numId"/></w:numPr>""")
                        }
                        val lineTwips = (block.lineSpacingMultiplier * 240f).roundToInt().coerceIn(200, 720)
                        val beforeTwips = (block.spaceBeforePt * 20f).roundToInt().coerceIn(0, 1440)
                        val afterTwips = (block.spaceAfterPt * 20f).roundToInt().coerceIn(0, 1440)
                        append("""<w:spacing w:before="$beforeTwips" w:after="$afterTwips" w:line="$lineTwips" w:lineRule="auto"/>""")
                        val indLeft = (block.leftIndentPt * 20f).roundToInt().coerceIn(0, 4320)
                        val indFirst = (block.firstLineIndentPt * 20f).roundToInt().coerceIn(0, 2880)
                        if (indLeft > 0 || indFirst > 0) {
                            append("""<w:ind w:left="$indLeft" w:firstLine="$indFirst"/>""")
                        }
                        val jc = when (block.alignment) {
                            DocxAlignment.LEFT -> "left"
                            DocxAlignment.CENTER -> "center"
                            DocxAlignment.RIGHT -> "right"
                            DocxAlignment.JUSTIFY -> "both"
                        }
                        append("""<w:jc w:val="$jc"/></w:pPr>""")

                        block.commentIds.forEach { cid ->
                            append("""<w:commentRangeStart w:id="${OpcPackageManager.escapeXml(cid)}"/>""")
                        }

                        block.runs.forEach { run ->
                            val runXml = buildRunXml(run)
                            val linked = if (!run.hyperlinkUrl.isNullOrBlank()) {
                                val rId = hyperlinkRels[run.hyperlinkUrl]
                                if (rId != null) """<w:hyperlink r:id="$rId">$runXml</w:hyperlink>""" else runXml
                            } else {
                                runXml
                            }
                            val tracked = when (run.trackChangeType) {
                                DocxTrackChangeType.INSERTION ->
                                    """<w:ins w:id="${UUID.randomUUID().toString().take(6)}" w:author="${OpcPackageManager.escapeXml(run.trackAuthor)}" w:date="${run.trackDateIso.ifBlank { OpcPackageManager.isoTimestampNow() }}">$linked</w:ins>"""
                                DocxTrackChangeType.DELETION ->
                                    """<w:del w:id="${UUID.randomUUID().toString().take(6)}" w:author="${OpcPackageManager.escapeXml(run.trackAuthor)}" w:date="${run.trackDateIso.ifBlank { OpcPackageManager.isoTimestampNow() }}">${buildRunXml(run, isDeletion = true)}</w:del>"""
                                DocxTrackChangeType.NONE -> linked
                            }
                            append(tracked)
                        }

                        block.commentIds.forEach { cid ->
                            append("""<w:commentRangeEnd w:id="${OpcPackageManager.escapeXml(cid)}"/>""")
                            append("""<w:r><w:commentReference w:id="${OpcPackageManager.escapeXml(cid)}"/></w:r>""")
                        }
                        block.footnoteIds.forEach { fid ->
                            append("""<w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteReference w:id="${OpcPackageManager.escapeXml(fid)}"/></w:r>""")
                        }
                        block.endnoteIds.forEach { eid ->
                            append("""<w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:endnoteReference w:id="${OpcPackageManager.escapeXml(eid)}"/></w:r>""")
                        }
                        append("</w:p>")
                    }

                    is DocxBlock.Table -> {
                        append("<w:tbl><w:tblPr>")
                        if (block.showBorders) {
                            append(
                                """<w:tblBorders><w:top w:val="single" w:sz="4" w:color="CBD5E1"/>""" +
                                    """<w:left w:val="single" w:sz="4" w:color="CBD5E1"/>""" +
                                    """<w:bottom w:val="single" w:sz="4" w:color="CBD5E1"/>""" +
                                    """<w:right w:val="single" w:sz="4" w:color="CBD5E1"/>""" +
                                    """<w:insideH w:val="single" w:sz="4" w:color="CBD5E1"/>""" +
                                    """<w:insideV w:val="single" w:sz="4" w:color="CBD5E1"/></w:tblBorders>"""
                            )
                        }
                        append("</w:tblPr>")
                        block.rows.forEachIndexed { rIdx, row ->
                            append("<w:tr>")
                            row.forEach { cellText ->
                                append("<w:tc><w:tcPr>")
                                if (rIdx == 0) {
                                    append("""<w:shd w:val="clear" w:color="auto" w:fill="${block.headerShadingHex}"/>""")
                                }
                                append("</w:tcPr><w:p><w:r>")
                                if (rIdx == 0) append("""<w:rPr><w:b/><w:color w:val="FFFFFF"/></w:rPr>""")
                                append("""<w:t xml:space="preserve">${OpcPackageManager.escapeXml(cellText)}</w:t>""")
                                append("</w:r></w:p></w:tc>")
                            }
                            append("</w:tr>")
                        }
                        append("</w:tbl>")
                    }

                    is DocxBlock.PageBreak -> {
                        append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
                    }

                    is DocxBlock.ImageBlock -> {
                        val rId = imageRels[block.mediaFileName] ?: "rId1"
                        val cx = (block.widthDp * 9525f).roundToInt()
                        val cy = (block.heightDp * 9525f).roundToInt()
                        val imgId = docImageId++
                        append("<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"$cx\" cy=\"$cy\"/>")
                        append("""<wp:docPr id="$imgId" name="${OpcPackageManager.escapeXml(block.mediaFileName)}"/>""")
                        append("""<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
                        append("""<pic:pic><pic:nvPicPr><pic:cNvPr id="$imgId" name="${OpcPackageManager.escapeXml(block.mediaFileName)}"/><pic:cNvPicPr/></pic:nvPicPr>""")
                        append("""<pic:blipFill><a:blip r:embed="$rId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""")
                        append("""<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""")
                        append("</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>")
                        if (block.caption.isNotBlank()) {
                            append("""<w:r><w:rPr><w:i/><w:sz w:val="20"/></w:rPr><w:t xml:space="preserve">${OpcPackageManager.escapeXml(block.caption)}</w:t></w:r>""")
                        }
                        append("</w:p>")
                    }

                    is DocxBlock.DrawingBlock -> {
                        append("""<w:p><w:r><w:t>[[DOCUDESK_DRAWING:${OpcPackageManager.escapeXml(block.id)}]]</w:t></w:r></w:p>""")
                    }

                    is DocxBlock.TextBoxBlock -> {
                        append("""<w:p><w:r><w:t>[[DOCUDESK_TEXTBOX:${OpcPackageManager.escapeXml(block.id)}]]</w:t></w:r></w:p>""")
                    }

                    is DocxBlock.EquationBlock -> {
                        append("""<m:oMathPara><m:oMath><m:r><m:t>${OpcPackageManager.escapeXml(block.displayUnicode)}</m:t></m:r></m:oMath></m:oMathPara>""")
                    }

                    is DocxBlock.TableOfContentsBlock -> {
                        append("""<w:sdt><w:sdtPr><w:docPartObj><w:docPartGallery w:val="Table of Contents"/><w:docPartUnique/></w:docPartObj></w:sdtPr><w:sdtContent>""")
                        append("""<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>${OpcPackageManager.escapeXml(block.title)}</w:t></w:r></w:p>""")
                        model.blocks.filterIsInstance<DocxBlock.Paragraph>().filter {
                            it.style.name.startsWith("HEADING_")
                        }.forEach { heading ->
                            val headingText = heading.runs.joinToString("") { it.text }
                            append("""<w:p><w:r><w:t>${OpcPackageManager.escapeXml(headingText)}</w:t></w:r></w:p>""")
                        }
                        append("</w:sdtContent></w:sdt>")
                    }
                }
            }

            append(
                """<w:sectPr><w:headerReference w:type="default" r:id="rId3"/>""" +
                    """<w:footerReference w:type="default" r:id="rId4"/>""" +
                    """<w:pgSz w:w="$pgW" w:h="$pgH"$orientAttr/>""" +
                    """<w:pgMar w:top="$mTop" w:right="$mRight" w:bottom="$mBottom" w:left="$mLeft"/>""" +
                    """<w:cols w:num="${model.columnCount.coerceIn(1, 3)}"/></w:sectPr>"""
            )
            append("</w:body></w:document>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildRunXml(run: DocxTextRun, isDeletion: Boolean = false): String = buildString {
        append("<w:r><w:rPr>")
        append("""<w:rFonts w:ascii="${OpcPackageManager.escapeXml(run.fontFamily)}" w:hAnsi="${OpcPackageManager.escapeXml(run.fontFamily)}"/>""")
        if (run.bold) append("<w:b/>")
        if (run.italic) append("<w:i/>")
        if (run.underline) append("""<w:u w:val="single"/>""")
        if (run.strikethrough) append("<w:strike/>")
        when (run.scriptMode) {
            DocxScriptMode.SUPERSCRIPT -> append("""<w:vertAlign w:val="superscript"/>""")
            DocxScriptMode.SUBSCRIPT -> append("""<w:vertAlign w:val="subscript"/>""")
            DocxScriptMode.NORMAL -> {}
        }
        val halfPts = (run.fontSizeSp * 2f).roundToInt().coerceIn(14, 144)
        append("""<w:sz w:val="$halfPts"/>""")
        if (!run.colorHex.isNullOrBlank()) {
            append("""<w:color w:val="${OpcPackageManager.escapeXml(run.colorHex)}"/>""")
        }
        if (!run.highlightColorHex.isNullOrBlank()) {
            append("""<w:highlight w:val="${mapHexToWordHighlight(run.highlightColorHex)}"/>""")
        }
        append("</w:rPr>")
        val tag = if (isDeletion) "w:delText" else "w:t"
        append("""<$tag xml:space="preserve">${OpcPackageManager.escapeXml(run.text)}</$tag>""")
        append("</w:r>")
    }

    private fun parseRelationships(xmlBytes: ByteArray?): Map<String, String> {
        if (xmlBytes == null) return emptyMap()
        val rels = mutableMapOf<String, String>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (id != null && target != null) rels[id] = target
                }
                event = parser.next()
            }
        }
        return rels
    }

    private fun parseCommentsXml(xmlBytes: ByteArray?): List<DocxComment> {
        if (xmlBytes == null) return emptyList()
        val out = mutableListOf<DocxComment>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
            var event = parser.eventType
            var id = ""
            var author = ""
            var date = ""
            var inComment = false
            var sb = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "comment") {
                        inComment = true
                        id = parser.getAttributeValue(null, "id") ?: parser.getAttributeValue(W_NS, "id") ?: ""
                        author = parser.getAttributeValue(null, "author") ?: parser.getAttributeValue(W_NS, "author") ?: "Reviewer"
                        date = parser.getAttributeValue(null, "date") ?: parser.getAttributeValue(W_NS, "date") ?: ""
                        sb = StringBuilder()
                    }
                    XmlPullParser.TEXT -> if (inComment) sb.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> if (parser.name == "comment" && inComment) {
                        out.add(
                            DocxComment(
                                id = id,
                                targetBlockId = "",
                                author = author,
                                text = sb.toString().trim(),
                                createdAtIso = date
                            )
                        )
                        inComment = false
                    }
                }
                event = parser.next()
            }
        }
        return out
    }

    private fun parseNotesXml(xmlBytes: ByteArray?, kind: DocxNoteKind): List<DocxNote> {
        if (xmlBytes == null) return emptyList()
        val out = mutableListOf<DocxNote>()
        val nodeTag = if (kind == DocxNoteKind.FOOTNOTE) "footnote" else "endnote"
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
            var event = parser.eventType
            var id = ""
            var inNote = false
            var sb = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == nodeTag) {
                        id = parser.getAttributeValue(null, "id") ?: parser.getAttributeValue(W_NS, "id") ?: ""
                        if (id != "-1" && id != "0") {
                            inNote = true
                            sb = StringBuilder()
                        }
                    }
                    XmlPullParser.TEXT -> if (inNote) sb.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> if (parser.name == nodeTag && inNote) {
                        out.add(
                            DocxNote(
                                id = id,
                                number = id.toIntOrNull() ?: (out.size + 1),
                                kind = kind,
                                targetBlockId = "",
                                text = sb.toString().trim()
                            )
                        )
                        inNote = false
                    }
                }
                event = parser.next()
            }
        }
        return out
    }

    private fun parseSimpleHeaderFooterXml(xmlBytes: ByteArray?): String {
        if (xmlBytes == null) return ""
        val sb = StringBuilder()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
            var event = parser.eventType
            var inT = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                    XmlPullParser.TEXT -> if (inT) sb.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> if (parser.name == "t") inT = false
                }
                event = parser.next()
            }
        }
        return sb.toString().trim()
    }

    private fun parseFooterXml(xmlBytes: ByteArray?): Pair<String, Boolean> {
        if (xmlBytes == null) return "" to true
        val raw = String(xmlBytes, StandardCharsets.UTF_8)
        val hasPageField = raw.contains("PAGE")
        val text = parseSimpleHeaderFooterXml(xmlBytes).replace("PAGE", "").trim()
        return text to hasPageField
    }

    private fun parseCustomExtrasXml(xmlBytes: ByteArray?): CustomExtrasPayload {
        if (xmlBytes == null) return CustomExtrasPayload()
        val drawings = mutableMapOf<String, DocxBlock.DrawingBlock>()
        val textBoxes = mutableMapOf<String, DocxBlock.TextBoxBlock>()
        val equations = mutableMapOf<String, DocxBlock.EquationBlock>()
        val citations = mutableListOf<DocxCitation>()
        var trackChanges = false
        var showMarkup = true

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "docudeskExtras" -> {
                            trackChanges = parser.getAttributeValue(null, "trackChanges") == "true"
                            showMarkup = parser.getAttributeValue(null, "showMarkup") != "false"
                        }
                        "drawing" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val title = parser.getAttributeValue(null, "title") ?: "Drawing"
                            val colorHex = parser.getAttributeValue(null, "colorHex") ?: "0F548C"
                            val strokeW = parser.getAttributeValue(null, "strokeWidth")?.toFloatOrNull() ?: 3f
                            val strokesJson = parser.getAttributeValue(null, "strokesJson") ?: "[]"
                            if (id.isNotEmpty()) {
                                drawings[id] = DocxBlock.DrawingBlock(
                                    id = id,
                                    title = title,
                                    strokes = DocuDeskConverters.jsonToStrokes(strokesJson),
                                    colorHex = colorHex,
                                    strokeWidthPt = strokeW
                                )
                            }
                        }
                        "textBox" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val text = parser.getAttributeValue(null, "text") ?: ""
                            val borderHex = parser.getAttributeValue(null, "borderColorHex") ?: "0F548C"
                            val bgHex = parser.getAttributeValue(null, "backgroundHex") ?: "F1F5F9"
                            if (id.isNotEmpty()) {
                                textBoxes[id] = DocxBlock.TextBoxBlock(id, text, borderHex, bgHex)
                            }
                        }
                        "citation" -> {
                            citations.add(
                                DocxCitation(
                                    id = parser.getAttributeValue(null, "id") ?: UUID.randomUUID().toString(),
                                    tag = parser.getAttributeValue(null, "tag") ?: "",
                                    author = parser.getAttributeValue(null, "author") ?: "",
                                    title = parser.getAttributeValue(null, "title") ?: "",
                                    year = parser.getAttributeValue(null, "year") ?: "",
                                    publisher = parser.getAttributeValue(null, "publisher") ?: "",
                                    style = parser.getAttributeValue(null, "style") ?: "APA 7th"
                                )
                            )
                        }
                    }
                }
                event = parser.next()
            }
        }
        return CustomExtrasPayload(drawings, textBoxes, equations, citations, trackChanges, showMarkup)
    }

    private fun buildCustomExtrasXml(model: DocxDocumentModel): ByteArray {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<docudeskExtras trackChanges="${model.trackChangesEnabled}" showMarkup="${model.showMarkup}">""")
            model.blocks.forEach { block ->
                when (block) {
                    is DocxBlock.DrawingBlock -> {
                        val json = DocuDeskConverters.strokesToJson(block.strokes)
                        append(
                            """<drawing id="${OpcPackageManager.escapeXml(block.id)}" """ +
                                """title="${OpcPackageManager.escapeXml(block.title)}" """ +
                                """colorHex="${OpcPackageManager.escapeXml(block.colorHex)}" """ +
                                """strokeWidth="${block.strokeWidthPt}" """ +
                                """strokesJson="${OpcPackageManager.escapeXml(json)}"/>"""
                        )
                    }
                    is DocxBlock.TextBoxBlock -> {
                        append(
                            """<textBox id="${OpcPackageManager.escapeXml(block.id)}" """ +
                                """text="${OpcPackageManager.escapeXml(block.text)}" """ +
                                """borderColorHex="${OpcPackageManager.escapeXml(block.borderColorHex)}" """ +
                                """backgroundHex="${OpcPackageManager.escapeXml(block.backgroundHex)}"/>"""
                        )
                    }
                    else -> {}
                }
            }
            model.citations.forEach { c ->
                append(
                    """<citation id="${OpcPackageManager.escapeXml(c.id)}" """ +
                        """tag="${OpcPackageManager.escapeXml(c.tag)}" """ +
                        """author="${OpcPackageManager.escapeXml(c.author)}" """ +
                        """title="${OpcPackageManager.escapeXml(c.title)}" """ +
                        """year="${OpcPackageManager.escapeXml(c.year)}" """ +
                        """publisher="${OpcPackageManager.escapeXml(c.publisher)}" """ +
                        """style="${OpcPackageManager.escapeXml(c.style)}"/>"""
                )
            }
            append("</docudeskExtras>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildHeaderXml(text: String): ByteArray = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:p><w:pPr><w:jc w:val="right"/></w:pPr><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="64748B"/></w:rPr><w:t xml:space="preserve">${OpcPackageManager.escapeXml(text)}</w:t></w:r></w:p>
        </w:hdr>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun buildFooterXml(text: String, showPageNumbers: Boolean): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        append("""<w:p><w:pPr><w:jc w:val="center"/></w:pPr>""")
        if (text.isNotBlank()) {
            append("""<w:r><w:rPr><w:sz w:val="18"/><w:color w:val="64748B"/></w:rPr><w:t xml:space="preserve">${OpcPackageManager.escapeXml(text)}  </w:t></w:r>""")
        }
        if (showPageNumbers) {
            append("""<w:r><w:rPr><w:sz w:val="18"/><w:color w:val="64748B"/></w:rPr><w:fldSimple w:instr="PAGE"/></w:r>""")
        }
        append("</w:p></w:ftr>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildCommentsXml(comments: List<DocxComment>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:comments xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        comments.forEach { c ->
            append("""<w:comment w:id="${OpcPackageManager.escapeXml(c.id)}" w:author="${OpcPackageManager.escapeXml(c.author)}" w:date="${OpcPackageManager.escapeXml(c.createdAtIso)}">""")
            append("""<w:p><w:r><w:t xml:space="preserve">${OpcPackageManager.escapeXml(c.text)}</w:t></w:r></w:p></w:comment>""")
        }
        append("</w:comments>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildNotesXml(notes: List<DocxNote>, isFootnote: Boolean): ByteArray {
        val root = if (isFootnote) "footnotes" else "endnotes"
        val item = if (isFootnote) "footnote" else "endnote"
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:$root xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("""<w:$item w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:$item>""")
            append("""<w:$item w:type="continuationSeparator" w:id="0"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:$item>""")
            notes.forEach { n ->
                append("""<w:$item w:id="${OpcPackageManager.escapeXml(n.id)}"><w:p><w:r><w:t xml:space="preserve">${OpcPackageManager.escapeXml(n.text)}</w:t></w:r></w:p></w:$item>""")
            }
            append("</w:$root>")
        }.toByteArray(StandardCharsets.UTF_8)
    }

    private fun styleToWordStyleId(style: DocxParagraphStyle): String = when (style) {
        DocxParagraphStyle.NORMAL -> "Normal"
        DocxParagraphStyle.TITLE -> "Title"
        DocxParagraphStyle.SUBTITLE -> "Subtitle"
        DocxParagraphStyle.HEADING_1 -> "Heading1"
        DocxParagraphStyle.HEADING_2 -> "Heading2"
        DocxParagraphStyle.HEADING_3 -> "Heading3"
        DocxParagraphStyle.HEADING_4 -> "Heading4"
        DocxParagraphStyle.HEADING_5 -> "Heading5"
        DocxParagraphStyle.HEADING_6 -> "Heading6"
        DocxParagraphStyle.QUOTE -> "Quote"
        DocxParagraphStyle.BULLET,
        DocxParagraphStyle.NUMBERED,
        DocxParagraphStyle.MULTILEVEL -> "ListParagraph"
    }

    private fun defaultFontSizeForStyle(style: DocxParagraphStyle): Float = when (style) {
        DocxParagraphStyle.TITLE -> 28f
        DocxParagraphStyle.SUBTITLE -> 15f
        DocxParagraphStyle.HEADING_1 -> 22f
        DocxParagraphStyle.HEADING_2 -> 18f
        DocxParagraphStyle.HEADING_3 -> 15f
        DocxParagraphStyle.HEADING_4 -> 13.5f
        DocxParagraphStyle.HEADING_5 -> 12.5f
        DocxParagraphStyle.HEADING_6 -> 11.5f
        else -> 12f
    }

    private fun matchMarginsPreset(t: Float, b: Float, l: Float, r: Float): DocxMarginsPreset {
        return DocxMarginsPreset.entries.firstOrNull { preset ->
            preset != DocxMarginsPreset.CUSTOM &&
                abs(preset.topIn - t) < 0.05f &&
                abs(preset.bottomIn - b) < 0.05f &&
                abs(preset.leftIn - l) < 0.05f &&
                abs(preset.rightIn - r) < 0.05f
        } ?: DocxMarginsPreset.CUSTOM
    }

    private fun mapWordHighlightToHex(valName: String?): String? = when (valName?.lowercase()) {
        "yellow" -> "FEF08A"
        "green" -> "BBF7D0"
        "cyan" -> "A5F3FC"
        "magenta" -> "F5D0FE"
        "red" -> "FECACA"
        "blue" -> "BFDBFE"
        "none", null -> null
        else -> valName
    }

    private fun mapHexToWordHighlight(hex: String): String = when (hex.uppercase()) {
        "FEF08A", "FACC15" -> "yellow"
        "BBF7D0", "4ADE80" -> "green"
        "A5F3FC", "22D3EE" -> "cyan"
        "F5D0FE", "E879F9" -> "magenta"
        "FECACA" -> "red"
        "BFDBFE" -> "blue"
        else -> "yellow"
    }

    private fun buildContentTypesXml(hasImages: Boolean): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        if (hasImages) {
            append("""<Default Extension="png" ContentType="image/png"/>""")
            append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
            append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
        }
        append("""<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""")
        append("""<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
        append("""<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""")
        append("""<Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>""")
        append("""<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""")
        append("""<Override PartName="/word/comments.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"/>""")
        append("""<Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/>""")
        append("""<Override PartName="/word/endnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("</Types>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun defaultPackageRelsXml(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
        </Relationships>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun buildDocumentRelsXml(
        hyperlinks: Map<String, String>,
        images: Map<String, String>
    ): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""")
        append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>""")
        append("""<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
        append("""<Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments" Target="comments.xml"/>""")
        append("""<Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/>""")
        append("""<Relationship Id="rId7" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes" Target="endnotes.xml"/>""")
        hyperlinks.forEach { (url, rId) ->
            append("""<Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${OpcPackageManager.escapeXml(url)}" TargetMode="External"/>""")
        }
        images.forEach { (fileName, rId) ->
            append("""<Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${OpcPackageManager.escapeXml(fileName)}"/>""")
        }
        append("</Relationships>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun defaultNumberingXml(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/></w:lvl></w:abstractNum>
            <w:abstractNum w:abstractNumId="2"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl></w:abstractNum>
            <w:abstractNum w:abstractNumId="3"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl><w:lvl w:ilvl="1"><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/></w:lvl></w:abstractNum>
            <w:num w:numId="1"><w:abstractNumId w:val="1"/></w:num>
            <w:num w:numId="2"><w:abstractNumId w:val="2"/></w:num>
            <w:num w:numId="3"><w:abstractNumId w:val="3"/></w:num>
        </w:numbering>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun defaultStylesXml(): ByteArray = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
            <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="56"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:rPr><w:i/><w:sz w:val="30"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:b/><w:sz w:val="44"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Heading4"><w:name w:val="heading 4"/><w:rPr><w:b/><w:sz w:val="27"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Heading5"><w:name w:val="heading 5"/><w:rPr><w:b/><w:sz w:val="25"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Heading6"><w:name w:val="heading 6"/><w:rPr><w:b/><w:sz w:val="23"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:rPr><w:i/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/></w:style>
        </w:styles>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
}
