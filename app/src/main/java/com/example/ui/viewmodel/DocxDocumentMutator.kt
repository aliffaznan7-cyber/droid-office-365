package com.example.ui.viewmodel

import com.example.core.ooxml.OpcPackageManager
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxCitation
import com.example.domain.model.DocxComment
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxMargins
import com.example.domain.model.DocxMarginsPreset
import com.example.domain.model.DocxNote
import com.example.domain.model.DocxNoteKind
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxScriptMode
import com.example.domain.model.DocxTextRun
import com.example.domain.model.DocxTrackChangeType
import com.example.domain.model.NormalizedPoint
import java.util.UUID

/**
 * Pure, deterministic state transformations for WordprocessingML documents,
 * supporting full Microsoft Word Home, Insert, Layout, References, and Review operations.
 */
object DocxDocumentMutator {

    fun computeWordAndCharCounts(model: DocxDocumentModel?): Pair<Int, Int> {
        if (model == null) return 0 to 0
        val sb = StringBuilder()
        model.blocks.forEach { block ->
            when (block) {
                is DocxBlock.Paragraph -> {
                    block.runs.filter { it.trackChangeType != DocxTrackChangeType.DELETION }
                        .forEach { sb.append(it.text).append(' ') }
                }
                is DocxBlock.Table -> {
                    block.rows.flatten().forEach { sb.append(it).append(' ') }
                }
                is DocxBlock.TextBoxBlock -> sb.append(block.text).append(' ')
                else -> {}
            }
        }
        val raw = sb.toString().trim()
        if (raw.isEmpty()) return 0 to 0
        val words = raw.split(Regex("\\s+")).count { it.isNotBlank() }
        val chars = raw.replace(" ", "").length
        return words to chars
    }

    fun updateParagraphText(
        model: DocxDocumentModel,
        blockId: String,
        newText: String
    ): DocxDocumentModel {
        val nowIso = OpcPackageManager.isoTimestampNow()
        val updatedBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == blockId) {
                if (model.trackChangesEnabled) {
                    val base = block.runs.firstOrNull { it.trackChangeType != DocxTrackChangeType.DELETION }
                        ?: block.runs.firstOrNull()
                        ?: DocxTextRun(UUID.randomUUID().toString(), "")
                    val oldText = block.runs
                        .filter { it.trackChangeType != DocxTrackChangeType.DELETION }
                        .joinToString("") { it.text }
                    val newRuns = buildList {
                        if (newText.startsWith(oldText) && newText.length > oldText.length) {
                            if (oldText.isNotEmpty()) {
                                add(base.copy(text = oldText, trackChangeType = DocxTrackChangeType.NONE))
                            }
                            add(
                                base.copy(
                                    id = UUID.randomUUID().toString(),
                                    text = newText.removePrefix(oldText),
                                    trackChangeType = DocxTrackChangeType.INSERTION,
                                    trackDateIso = nowIso
                                )
                            )
                        } else {
                            if (oldText.isNotEmpty()) {
                                add(
                                    base.copy(
                                        id = UUID.randomUUID().toString(),
                                        text = oldText,
                                        trackChangeType = DocxTrackChangeType.DELETION,
                                        trackDateIso = nowIso
                                    )
                                )
                            }
                            if (newText.isNotEmpty()) {
                                add(
                                    base.copy(
                                        id = UUID.randomUUID().toString(),
                                        text = newText,
                                        trackChangeType = DocxTrackChangeType.INSERTION,
                                        trackDateIso = nowIso
                                    )
                                )
                            }
                        }
                    }.ifEmpty { listOf(base.copy(text = "")) }
                    block.copy(runs = newRuns)
                } else {
                    val baseRun = block.runs.firstOrNull() ?: DocxTextRun(UUID.randomUUID().toString(), "")
                    block.copy(runs = listOf(baseRun.copy(text = newText)))
                }
            } else block
        }
        return model.copy(blocks = updatedBlocks)
    }

    fun updateSelectedParagraphRuns(
        model: DocxDocumentModel,
        blockId: String,
        transform: (DocxTextRun) -> DocxTextRun
    ): DocxDocumentModel {
        val updated = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == blockId) {
                val nextRuns = if (block.runs.isEmpty()) {
                    listOf(transform(DocxTextRun(UUID.randomUUID().toString(), "")))
                } else {
                    block.runs.map(transform)
                }
                block.copy(runs = nextRuns)
            } else block
        }
        return model.copy(blocks = updated)
    }

    fun updateSelectedParagraph(
        model: DocxDocumentModel,
        blockId: String,
        transform: (DocxBlock.Paragraph) -> DocxBlock.Paragraph
    ): DocxDocumentModel {
        val updated = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == blockId) transform(block) else block
        }
        return model.copy(blocks = updated)
    }

    fun applyParagraphStyle(
        model: DocxDocumentModel,
        blockId: String,
        style: DocxParagraphStyle
    ): DocxDocumentModel {
        val size = when (style) {
            DocxParagraphStyle.TITLE -> 28f
            DocxParagraphStyle.SUBTITLE -> 15f
            DocxParagraphStyle.HEADING_1 -> 22f
            DocxParagraphStyle.HEADING_2 -> 17f
            DocxParagraphStyle.HEADING_3 -> 14f
            DocxParagraphStyle.HEADING_4 -> 13f
            DocxParagraphStyle.HEADING_5 -> 12.5f
            DocxParagraphStyle.HEADING_6 -> 12f
            else -> 12f
        }
        val isHeading = style.name.startsWith("HEADING_") || style == DocxParagraphStyle.TITLE
        return updateSelectedParagraph(model, blockId) { para ->
            para.copy(
                style = style,
                runs = para.runs.map {
                    it.copy(
                        fontSizeSp = size,
                        bold = isHeading,
                        italic = style == DocxParagraphStyle.QUOTE || style == DocxParagraphStyle.SUBTITLE
                    )
                }
            )
        }
    }

    fun clearFormatting(model: DocxDocumentModel, blockId: String): DocxDocumentModel {
        return updateSelectedParagraph(model, blockId) { para ->
            para.copy(
                style = DocxParagraphStyle.NORMAL,
                listLevel = 0,
                leftIndentPt = 0f,
                firstLineIndentPt = 0f,
                runs = para.runs.map {
                    it.copy(
                        fontFamily = "Merriweather",
                        bold = false,
                        italic = false,
                        underline = false,
                        strikethrough = false,
                        scriptMode = DocxScriptMode.NORMAL,
                        fontSizeSp = 12f,
                        colorHex = null,
                        highlightColorHex = null
                    )
                }
            )
        }
    }

    fun insertBlockAfter(
        model: DocxDocumentModel,
        afterBlockId: String?,
        newBlock: DocxBlock
    ): DocxDocumentModel {
        val idx = if (afterBlockId == null) -1 else model.blocks.indexOfFirst { it.id == afterBlockId }
        val next = model.blocks.toMutableList()
        if (idx in next.indices) {
            next.add(idx + 1, newBlock)
        } else {
            next.add(newBlock)
        }
        return model.copy(blocks = next)
    }

    fun createParagraph(style: DocxParagraphStyle): DocxBlock.Paragraph {
        val defaultText = when (style) {
            DocxParagraphStyle.TITLE -> "Document Title"
            DocxParagraphStyle.SUBTITLE -> "Document Subtitle"
            DocxParagraphStyle.HEADING_1 -> "Section 1: Overview"
            DocxParagraphStyle.HEADING_2 -> "Subsection Heading"
            DocxParagraphStyle.HEADING_3 -> "Subtopic"
            DocxParagraphStyle.HEADING_4 -> "Detail Heading"
            DocxParagraphStyle.HEADING_5 -> "Minor Heading"
            DocxParagraphStyle.HEADING_6 -> "Note Heading"
            DocxParagraphStyle.QUOTE -> "Quoted excerpt or executive summary note."
            DocxParagraphStyle.BULLET -> "Bulleted item"
            DocxParagraphStyle.NUMBERED -> "Numbered item"
            DocxParagraphStyle.MULTILEVEL -> "Multilevel item"
            DocxParagraphStyle.NORMAL -> "New paragraph..."
        }
        val size = when (style) {
            DocxParagraphStyle.TITLE -> 28f
            DocxParagraphStyle.HEADING_1 -> 22f
            DocxParagraphStyle.HEADING_2 -> 17f
            DocxParagraphStyle.HEADING_3 -> 14f
            else -> 12f
        }
        return DocxBlock.Paragraph(
            id = UUID.randomUUID().toString(),
            style = style,
            runs = listOf(
                DocxTextRun(
                    id = UUID.randomUUID().toString(),
                    text = defaultText,
                    bold = style.name.startsWith("HEADING_") || style == DocxParagraphStyle.TITLE,
                    italic = style == DocxParagraphStyle.QUOTE || style == DocxParagraphStyle.SUBTITLE,
                    fontSizeSp = size
                )
            )
        )
    }

    fun createTable(rows: Int, cols: Int): DocxBlock.Table {
        val safeR = rows.coerceIn(1, 12)
        val safeC = cols.coerceIn(1, 8)
        val matrix = List(safeR) { r ->
            List(safeC) { c ->
                if (r == 0) "Header ${c + 1}" else "Cell ${r},${c + 1}"
            }
        }
        return DocxBlock.Table(id = UUID.randomUUID().toString(), rows = matrix)
    }

    fun findMatches(model: DocxDocumentModel, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return model.blocks.mapNotNull { block ->
            when (block) {
                is DocxBlock.Paragraph -> {
                    val txt = block.runs.joinToString("") { it.text }
                    if (txt.contains(q, ignoreCase = true)) block.id else null
                }
                is DocxBlock.Table -> {
                    if (block.rows.flatten().any { it.contains(q, ignoreCase = true) }) block.id else null
                }
                is DocxBlock.TextBoxBlock -> if (block.text.contains(q, ignoreCase = true)) block.id else null
                else -> null
            }
        }
    }

    fun replaceOneInBlock(
        model: DocxDocumentModel,
        blockId: String,
        query: String,
        replacement: String
    ): DocxDocumentModel {
        if (query.isEmpty()) return model
        val updated = model.blocks.map { block ->
            if (block.id != blockId) return@map block
            when (block) {
                is DocxBlock.Paragraph -> {
                    val full = block.runs.joinToString("") { it.text }
                    val idx = full.indexOf(query, ignoreCase = true)
                    if (idx >= 0) {
                        val nextFull = full.replaceRange(idx, idx + query.length, replacement)
                        val base = block.runs.firstOrNull() ?: DocxTextRun(UUID.randomUUID().toString(), "")
                        block.copy(runs = listOf(base.copy(text = nextFull)))
                    } else block
                }
                is DocxBlock.Table -> {
                    var replaced = false
                    val nextRows = block.rows.map { row ->
                        row.map { cell ->
                            if (!replaced && cell.contains(query, ignoreCase = true)) {
                                replaced = true
                                val idx = cell.indexOf(query, ignoreCase = true)
                                cell.replaceRange(idx, idx + query.length, replacement)
                            } else cell
                        }
                    }
                    block.copy(rows = nextRows)
                }
                is DocxBlock.TextBoxBlock -> {
                    val idx = block.text.indexOf(query, ignoreCase = true)
                    if (idx >= 0) block.copy(text = block.text.replaceRange(idx, idx + query.length, replacement)) else block
                }
                else -> block
            }
        }
        return model.copy(blocks = updated)
    }

    fun replaceAll(model: DocxDocumentModel, query: String, replacement: String): Pair<DocxDocumentModel, Int> {
        if (query.isEmpty()) return model to 0
        var count = 0
        val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        val updated = model.blocks.map { block ->
            when (block) {
                is DocxBlock.Paragraph -> {
                    val nextRuns = block.runs.map { run ->
                        val matches = regex.findAll(run.text).count()
                        count += matches
                        if (matches > 0) run.copy(text = regex.replace(run.text, Regex.escapeReplacement(replacement)))
                        else run
                    }
                    block.copy(runs = nextRuns)
                }
                is DocxBlock.Table -> {
                    val nextRows = block.rows.map { row ->
                        row.map { cell ->
                            val matches = regex.findAll(cell).count()
                            count += matches
                            if (matches > 0) regex.replace(cell, Regex.escapeReplacement(replacement)) else cell
                        }
                    }
                    block.copy(rows = nextRows)
                }
                is DocxBlock.TextBoxBlock -> {
                    val matches = regex.findAll(block.text).count()
                    count += matches
                    if (matches > 0) block.copy(text = regex.replace(block.text, Regex.escapeReplacement(replacement)))
                    else block
                }
                else -> block
            }
        }
        return model.copy(blocks = updated) to count
    }

    fun acceptTrackedChange(model: DocxDocumentModel, blockId: String, runId: String): DocxDocumentModel {
        val nextBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == blockId) {
                val nextRuns = block.runs.mapNotNull { run ->
                    if (run.id != runId) run
                    else when (run.trackChangeType) {
                        DocxTrackChangeType.INSERTION -> run.copy(trackChangeType = DocxTrackChangeType.NONE)
                        DocxTrackChangeType.DELETION -> null
                        DocxTrackChangeType.NONE -> run
                    }
                }.ifEmpty { listOf(DocxTextRun(UUID.randomUUID().toString(), "")) }
                block.copy(runs = nextRuns)
            } else block
        }
        return model.copy(blocks = nextBlocks)
    }

    fun rejectTrackedChange(model: DocxDocumentModel, blockId: String, runId: String): DocxDocumentModel {
        val nextBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == blockId) {
                val nextRuns = block.runs.mapNotNull { run ->
                    if (run.id != runId) run
                    else when (run.trackChangeType) {
                        DocxTrackChangeType.INSERTION -> null
                        DocxTrackChangeType.DELETION -> run.copy(trackChangeType = DocxTrackChangeType.NONE)
                        DocxTrackChangeType.NONE -> run
                    }
                }.ifEmpty { listOf(DocxTextRun(UUID.randomUUID().toString(), "")) }
                block.copy(runs = nextRuns)
            } else block
        }
        return model.copy(blocks = nextBlocks)
    }

    fun acceptAllTrackedChanges(model: DocxDocumentModel): DocxDocumentModel {
        val nextBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph) {
                val nextRuns = block.runs.mapNotNull { run ->
                    when (run.trackChangeType) {
                        DocxTrackChangeType.DELETION -> null
                        DocxTrackChangeType.INSERTION, DocxTrackChangeType.NONE ->
                            run.copy(trackChangeType = DocxTrackChangeType.NONE)
                    }
                }.ifEmpty { listOf(DocxTextRun(UUID.randomUUID().toString(), "")) }
                block.copy(runs = nextRuns)
            } else block
        }
        return model.copy(blocks = nextBlocks)
    }

    fun rejectAllTrackedChanges(model: DocxDocumentModel): DocxDocumentModel {
        val nextBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph) {
                val nextRuns = block.runs.mapNotNull { run ->
                    when (run.trackChangeType) {
                        DocxTrackChangeType.INSERTION -> null
                        DocxTrackChangeType.DELETION, DocxTrackChangeType.NONE ->
                            run.copy(trackChangeType = DocxTrackChangeType.NONE)
                    }
                }.ifEmpty { listOf(DocxTextRun(UUID.randomUUID().toString(), "")) }
                block.copy(runs = nextRuns)
            } else block
        }
        return model.copy(blocks = nextBlocks)
    }

    fun applyMarginsPreset(preset: DocxMarginsPreset): DocxMargins = DocxMargins(
        preset = preset,
        topInches = preset.topIn,
        bottomInches = preset.bottomIn,
        leftInches = preset.leftIn,
        rightInches = preset.rightIn
    )

    fun formatCitationEntry(citation: DocxCitation): String {
        val pub = if (citation.publisher.isNotBlank()) " ${citation.publisher}." else ""
        return when (citation.style.uppercase()) {
            "IEEE" -> "[${citation.tag}] ${citation.author}, \"${citation.title},\"$pub ${citation.year}."
            "MLA" -> "${citation.author}. \"${citation.title}.\"$pub ${citation.year}."
            else -> "${citation.author} (${citation.year}). ${citation.title}.$pub"
        }
    }

    fun insertNote(
        model: DocxDocumentModel,
        targetBlockId: String,
        kind: DocxNoteKind,
        text: String
    ): DocxDocumentModel {
        val existingList = if (kind == DocxNoteKind.FOOTNOTE) model.footnotes else model.endnotes
        val nextNum = (existingList.maxOfOrNull { it.number } ?: 0) + 1
        val note = DocxNote(
            id = nextNum.toString(),
            number = nextNum,
            kind = kind,
            targetBlockId = targetBlockId,
            text = text.trim()
        )
        val nextBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == targetBlockId) {
                if (kind == DocxNoteKind.FOOTNOTE) block.copy(footnoteIds = block.footnoteIds + note.id)
                else block.copy(endnoteIds = block.endnoteIds + note.id)
            } else block
        }
        return if (kind == DocxNoteKind.FOOTNOTE) {
            model.copy(blocks = nextBlocks, footnotes = model.footnotes + note)
        } else {
            model.copy(blocks = nextBlocks, endnotes = model.endnotes + note)
        }
    }

    fun addComment(
        model: DocxDocumentModel,
        targetBlockId: String,
        text: String
    ): DocxDocumentModel {
        val nextId = ((model.comments.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()
        val comment = DocxComment(
            id = nextId,
            targetBlockId = targetBlockId,
            text = text.trim(),
            createdAtIso = OpcPackageManager.isoTimestampNow()
        )
        val nextBlocks = model.blocks.map { block ->
            if (block is DocxBlock.Paragraph && block.id == targetBlockId) {
                block.copy(commentIds = block.commentIds + comment.id)
            } else block
        }
        return model.copy(blocks = nextBlocks, comments = model.comments + comment)
    }

    fun createDrawingBlock(
        title: String,
        strokes: List<List<NormalizedPoint>>,
        colorHex: String
    ): DocxBlock.DrawingBlock = DocxBlock.DrawingBlock(
        id = UUID.randomUUID().toString(),
        title = title.ifBlank { "Stylus Sketch" },
        strokes = strokes,
        colorHex = colorHex
    )
}
