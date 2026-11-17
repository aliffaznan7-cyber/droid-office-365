package com.example.domain.model

enum class DocumentFormat(val extension: String, val mimeType: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    DOCX(
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Word"
    ),
    XLSX(
        "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Excel"
    );

    companion object {
        fun fromExtension(ext: String): DocumentFormat {
            return when (ext.lowercase().trimStart('.')) {
                "pdf" -> PDF
                "docx" -> DOCX
                "xlsx" -> XLSX
                else -> PDF
            }
        }
    }
}

enum class RibbonTab(val title: String) {
    HOME("Home"),
    INSERT("Insert"),
    LAYOUT("Layout"),
    REFERENCES("References"),
    REVIEW("Review"),
    VIEW("View"),
    PDF_ANNOTATE("PDF Ink & Edit")
}

enum class PdfEditorTool(val label: String) {
    BROWSE("Browse / Pan"),
    SELECT_TEXT("Select Text"),
    HIGHLIGHT("Highlight"),
    INK_PEN("Stylus Ink"),
    ERASER("Eraser"),
    TEXT_BOX("Text Box"),
    SIGNATURE("Signature")
}

enum class SyncConflictState {
    CLEAN,
    DIRTY_LOCAL,
    AUTOSAVE_RECOVERABLE,
    CONFLICT_DETECTED
}

data class DocumentTab(
    val documentId: String,
    val title: String,
    val format: DocumentFormat,
    val filePath: String,
    val isDirty: Boolean = false,
    val currentPage: Int = 0,
    val zoomScale: Float = 1.0f
)

data class CoreDocumentProperties(
    val title: String = "",
    val creator: String = "DocuDesk User",
    val subject: String = "",
    val description: String = "",
    val department: String = "Engineering",
    val classification: String = "Internal",
    val created: String = "",
    val modified: String = ""
)

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

enum class PdfAnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    INK,
    TEXT_BOX,
    SIGNATURE
}

data class PdfAnnotation(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val type: PdfAnnotationType,
    val colorArgb: Long,
    val strokeWidthNormalized: Float = 0.004f,
    val points: List<NormalizedPoint> = emptyList(),
    val bounds: NormalizedRect? = null,
    val textContent: String = "",
    val fontSizeSp: Float = 13f,
    val author: String = "DocuDesk User",
    val createdAt: Long = System.currentTimeMillis(),
    val isFlattened: Boolean = false
)

data class PdfTextRun(
    val text: String,
    val pageIndex: Int,
    val bounds: NormalizedRect
)

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String,
    val bounds: NormalizedRect
)

data class SignaturePreset(
    val id: String,
    val label: String,
    val strokes: List<List<NormalizedPoint>>,
    val colorArgb: Long,
    val createdAt: Long
)

// DOCX WordprocessingML Domain Models (Part 1 Section 4.3 + Full Desktop Word Suite)
enum class DocxParagraphStyle(val label: String) {
    NORMAL("Normal"),
    TITLE("Title"),
    SUBTITLE("Subtitle"),
    HEADING_1("Heading 1"),
    HEADING_2("Heading 2"),
    HEADING_3("Heading 3"),
    HEADING_4("Heading 4"),
    HEADING_5("Heading 5"),
    HEADING_6("Heading 6"),
    QUOTE("Quote"),
    BULLET("Bulleted List"),
    NUMBERED("Numbered List"),
    MULTILEVEL("Multilevel List")
}

enum class DocxAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

enum class DocxScriptMode {
    NORMAL,
    SUPERSCRIPT,
    SUBSCRIPT
}

enum class DocxTrackChangeType {
    NONE,
    INSERTION,
    DELETION
}

enum class DocxMarginsPreset(
    val label: String,
    val topIn: Float,
    val bottomIn: Float,
    val leftIn: Float,
    val rightIn: Float
) {
    NORMAL("Normal (1.0\")", 1.0f, 1.0f, 1.0f, 1.0f),
    NARROW("Narrow (0.5\")", 0.5f, 0.5f, 0.5f, 0.5f),
    MODERATE("Moderate (0.75\")", 0.75f, 0.75f, 0.75f, 0.75f),
    WIDE("Wide (1.5\")", 1.0f, 1.0f, 1.5f, 1.5f),
    CUSTOM("Custom", 1.0f, 1.0f, 1.0f, 1.0f)
}

enum class DocxOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class DocxPaperSize(val label: String, val widthIn: Float, val heightIn: Float) {
    A4("A4 (210 × 297 mm)", 8.27f, 11.69f),
    LETTER("Letter (8.5 × 11 in)", 8.5f, 11.0f),
    LEGAL("Legal (8.5 × 14 in)", 8.5f, 14.0f)
}

enum class DocxViewMode(val label: String) {
    PRINT_LAYOUT("Print Layout"),
    WEB_LAYOUT("Web Layout")
}

enum class DocxPaperTone(val label: String) {
    CRISP_WHITE("Paper White"),
    SEPIA_COMFORT("Sepia Comfort"),
    DARK_NIGHT("Dark Comfort")
}

data class DocxMargins(
    val preset: DocxMarginsPreset = DocxMarginsPreset.NORMAL,
    val topInches: Float = 1.0f,
    val bottomInches: Float = 1.0f,
    val leftInches: Float = 1.0f,
    val rightInches: Float = 1.0f
)

data class DocxTextRun(
    val id: String,
    val text: String,
    val fontFamily: String = "Merriweather",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val scriptMode: DocxScriptMode = DocxScriptMode.NORMAL,
    val fontSizeSp: Float = 12f,
    val colorHex: String? = null,
    val highlightColorHex: String? = null,
    val hyperlinkUrl: String? = null,
    val trackChangeType: DocxTrackChangeType = DocxTrackChangeType.NONE,
    val trackAuthor: String = "DocuDesk User",
    val trackDateIso: String = ""
)

data class DocxComment(
    val id: String,
    val targetBlockId: String,
    val author: String = "DocuDesk User",
    val text: String,
    val createdAtIso: String,
    val resolved: Boolean = false
)

enum class DocxNoteKind {
    FOOTNOTE,
    ENDNOTE
}

data class DocxNote(
    val id: String,
    val number: Int,
    val kind: DocxNoteKind,
    val targetBlockId: String,
    val text: String
)

data class DocxCitation(
    val id: String,
    val tag: String,
    val author: String,
    val title: String,
    val year: String,
    val publisher: String = "",
    val style: String = "APA 7th"
)

sealed interface DocxBlock {
    val id: String

    data class Paragraph(
        override val id: String,
        val style: DocxParagraphStyle = DocxParagraphStyle.NORMAL,
        val alignment: DocxAlignment = DocxAlignment.LEFT,
        val listLevel: Int = 0,
        val lineSpacingMultiplier: Float = 1.15f,
        val spaceBeforePt: Float = 0f,
        val spaceAfterPt: Float = 8f,
        val leftIndentPt: Float = 0f,
        val firstLineIndentPt: Float = 0f,
        val runs: List<DocxTextRun> = emptyList(),
        val commentIds: List<String> = emptyList(),
        val footnoteIds: List<String> = emptyList(),
        val endnoteIds: List<String> = emptyList()
    ) : DocxBlock

    data class Table(
        override val id: String,
        val rows: List<List<String>>,
        val headerShadingHex: String = "1E3A8A",
        val showBorders: Boolean = true
    ) : DocxBlock

    data class PageBreak(
        override val id: String
    ) : DocxBlock

    data class ImageBlock(
        override val id: String,
        val mediaFileName: String,
        val localFilePath: String,
        val widthDp: Float = 360f,
        val heightDp: Float = 220f,
        val caption: String = ""
    ) : DocxBlock

    data class DrawingBlock(
        override val id: String,
        val title: String = "Stylus Sketch",
        val strokes: List<List<NormalizedPoint>> = emptyList(),
        val colorHex: String = "0F548C",
        val strokeWidthPt: Float = 3.0f,
        val widthDp: Float = 440f,
        val heightDp: Float = 200f
    ) : DocxBlock

    data class TextBoxBlock(
        override val id: String,
        val text: String,
        val borderColorHex: String = "0F548C",
        val backgroundHex: String = "F1F5F9"
    ) : DocxBlock

    data class EquationBlock(
        override val id: String,
        val expression: String,
        val displayUnicode: String
    ) : DocxBlock

    data class TableOfContentsBlock(
        override val id: String,
        val title: String = "Table of Contents"
    ) : DocxBlock
}

data class DocxDocumentModel(
    val documentId: String,
    val coreProperties: CoreDocumentProperties = CoreDocumentProperties(),
    val blocks: List<DocxBlock> = emptyList(),
    val margins: DocxMargins = DocxMargins(),
    val orientation: DocxOrientation = DocxOrientation.PORTRAIT,
    val paperSize: DocxPaperSize = DocxPaperSize.A4,
    val columnCount: Int = 1,
    val headerText: String = "",
    val footerText: String = "",
    val showPageNumbers: Boolean = true,
    val comments: List<DocxComment> = emptyList(),
    val footnotes: List<DocxNote> = emptyList(),
    val endnotes: List<DocxNote> = emptyList(),
    val citations: List<DocxCitation> = emptyList(),
    val trackChangesEnabled: Boolean = false,
    val showMarkup: Boolean = true
) {
    val pageMarginInches: Float
        get() = margins.leftInches
}

// XLSX SpreadsheetML Domain Models (Part 1 Section 4.4)
data class XlsxCell(
    val row: Int,
    val col: Int,
    val rawInput: String = "",
    val formula: String? = null,
    val cachedValue: String = "",
    val displayValue: String = "",
    val numberFormat: String = "General",
    val isBold: Boolean = false,
    val backgroundHex: String? = null
)

data class XlsxWorksheet(
    val sheetId: Int,
    val name: String,
    val cells: Map<Pair<Int, Int>, XlsxCell> = emptyMap(),
    val maxRow: Int = 40,
    val maxCol: Int = 14
)

data class XlsxWorkbookModel(
    val documentId: String,
    val coreProperties: CoreDocumentProperties = CoreDocumentProperties(),
    val worksheets: List<XlsxWorksheet> = emptyList(),
    val activeSheetIndex: Int = 0
)

sealed interface WorkspaceLoadState {
    data object Loading : WorkspaceLoadState
    data class Ready(val message: String? = null) : WorkspaceLoadState
    data class Error(val message: String) : WorkspaceLoadState
}
