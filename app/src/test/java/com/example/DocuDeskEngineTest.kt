package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.ooxml.DocxEngine
import com.example.core.ooxml.SpreadsheetFormulaEngine
import com.example.core.ooxml.XlsxEngine
import com.example.core.pdf.PdfObjectStructure
import com.example.core.pdf.PdfVectorFlattener
import com.example.data.local.DocuDeskDatabase
import com.example.data.local.DocumentEntity
import com.example.domain.model.CoreDocumentProperties
import com.example.domain.model.DocumentFormat
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxTextRun
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.NormalizedRect
import com.example.domain.model.PdfAnnotation
import com.example.domain.model.PdfAnnotationType
import com.example.domain.model.XlsxCell
import com.example.domain.model.XlsxWorkbookModel
import com.example.domain.model.XlsxWorksheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocuDeskEngineTest {

    private lateinit var db: DocuDeskDatabase
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DocuDeskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(context.cacheDir, "docudesk_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `room stores and streams document metadata`() = runBlocking {
        val doc = DocumentEntity(
            id = "doc_1",
            title = "Feasibility Report",
            fileName = "report.pdf",
            filePath = "/tmp/report.pdf",
            format = DocumentFormat.PDF,
            fileSizeBytes = 2048,
            pageOrSheetCount = 19
        )
        db.documentDao().upsertDocument(doc)
        val all = db.documentDao().observeAllDocuments().first()
        assertEquals(1, all.size)
        assertEquals("Feasibility Report", all.first().title)
    }

    @Test
    fun `docx engine round trips headings paragraphs and tables`() = runBlocking {
        val outFile = File(tempDir, "roundtrip.docx")
        val model = DocxDocumentModel(
            documentId = "d1",
            coreProperties = CoreDocumentProperties(title = "Spec"),
            blocks = listOf(
                DocxBlock.Paragraph(
                    id = "p1",
                    style = DocxParagraphStyle.HEADING_1,
                    runs = listOf(DocxTextRun("r1", "Architecture Heading", bold = true))
                ),
                DocxBlock.Table(
                    id = "t1",
                    rows = listOf(listOf("Col A", "Col B"), listOf("Val 1", "Val 2"))
                )
            )
        )
        DocxEngine.saveDocument(model, outFile)
        val loaded = DocxEngine.loadDocument("d1", outFile)
        assertEquals(2, loaded.blocks.size)
        val heading = loaded.blocks[0] as DocxBlock.Paragraph
        assertEquals(DocxParagraphStyle.HEADING_1, heading.style)
        assertEquals("Architecture Heading", heading.runs.first().text)
        val table = loaded.blocks[1] as DocxBlock.Table
        assertEquals("Val 2", table.rows[1][1])
    }

    @Test
    fun `docx engine round trips full desktop word features without data loss`() = runBlocking {
        val outFile = File(tempDir, "full_word_suite.docx")
        val model = DocxDocumentModel(
            documentId = "word_full",
            coreProperties = CoreDocumentProperties(title = "Full Desktop Word"),
            margins = com.example.domain.model.DocxMargins(
                preset = com.example.domain.model.DocxMarginsPreset.NARROW,
                topInches = 0.5f,
                bottomInches = 0.5f,
                leftInches = 0.5f,
                rightInches = 0.5f
            ),
            orientation = com.example.domain.model.DocxOrientation.LANDSCAPE,
            paperSize = com.example.domain.model.DocxPaperSize.LETTER,
            columnCount = 2,
            headerText = "DocuDesk Header",
            footerText = "DocuDesk Footer",
            showPageNumbers = true,
            trackChangesEnabled = true,
            comments = listOf(
                com.example.domain.model.DocxComment(
                    id = "1",
                    targetBlockId = "p_rich",
                    text = "Check section 2 metrics",
                    createdAtIso = "2025-05-01T10:00:00Z"
                )
            ),
            footnotes = listOf(
                com.example.domain.model.DocxNote(
                    id = "1",
                    number = 1,
                    kind = com.example.domain.model.DocxNoteKind.FOOTNOTE,
                    targetBlockId = "p_rich",
                    text = "See ISO/IEC 29500"
                )
            ),
            endnotes = listOf(
                com.example.domain.model.DocxNote(
                    id = "1",
                    number = 1,
                    kind = com.example.domain.model.DocxNoteKind.ENDNOTE,
                    targetBlockId = "p_rich",
                    text = "Appendix reference"
                )
            ),
            citations = listOf(
                com.example.domain.model.DocxCitation(
                    id = "c1",
                    tag = "M365",
                    author = "Architect Team",
                    title = "Feasibility Report",
                    year = "2025",
                    style = "APA 7th"
                )
            ),
            blocks = listOf(
                DocxBlock.TableOfContentsBlock(id = "toc1"),
                DocxBlock.Paragraph(
                    id = "p_h4",
                    style = DocxParagraphStyle.HEADING_4,
                    runs = listOf(DocxTextRun("rh4", "Heading Four", bold = true))
                ),
                DocxBlock.Paragraph(
                    id = "p_rich",
                    style = DocxParagraphStyle.MULTILEVEL,
                    listLevel = 1,
                    runs = listOf(
                        DocxTextRun(
                            id = "r_rich",
                            text = "Highlighted Superscript",
                            fontFamily = "Inter",
                            strikethrough = true,
                            scriptMode = com.example.domain.model.DocxScriptMode.SUPERSCRIPT,
                            highlightColorHex = "FEF08A",
                            hyperlinkUrl = "https://example.org",
                            trackChangeType = com.example.domain.model.DocxTrackChangeType.INSERTION
                        )
                    ),
                    commentIds = listOf("1"),
                    footnoteIds = listOf("1"),
                    endnoteIds = listOf("1")
                ),
                DocxBlock.PageBreak(id = "pb1"),
                DocxBlock.EquationBlock(id = "eq1", expression = "E = mc²", displayUnicode = "E = mc²"),
                DocxBlock.TextBoxBlock(id = "tb1", text = "Callout summary"),
                DocxBlock.DrawingBlock(
                    id = "dr1",
                    title = "Sketch",
                    strokes = listOf(listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.8f, 0.8f)))
                )
            )
        )

        DocxEngine.saveDocument(model, outFile)
        val loaded = DocxEngine.loadDocument("word_full", outFile, File(tempDir, "media"))

        assertEquals(com.example.domain.model.DocxOrientation.LANDSCAPE, loaded.orientation)
        assertEquals(com.example.domain.model.DocxPaperSize.LETTER, loaded.paperSize)
        assertEquals(2, loaded.columnCount)
        assertEquals(0.5f, loaded.margins.leftInches, 0.05f)
        assertEquals("DocuDesk Header", loaded.headerText)
        assertEquals("DocuDesk Footer", loaded.footerText)
        assertTrue(loaded.showPageNumbers)
        assertTrue(loaded.trackChangesEnabled)
        assertEquals(1, loaded.comments.size)
        assertEquals(1, loaded.footnotes.size)
        assertEquals(1, loaded.endnotes.size)
        assertEquals(1, loaded.citations.size)
        assertTrue(loaded.blocks.any { it is DocxBlock.TableOfContentsBlock })
        assertTrue(loaded.blocks.any { it is DocxBlock.PageBreak })
        assertTrue(loaded.blocks.any { it is DocxBlock.EquationBlock && it.displayUnicode == "E = mc²" })
        assertTrue(loaded.blocks.any { it is DocxBlock.TextBoxBlock && it.text == "Callout summary" })
        assertTrue(loaded.blocks.any { it is DocxBlock.DrawingBlock && it.strokes.isNotEmpty() })

        val h4 = loaded.blocks.filterIsInstance<DocxBlock.Paragraph>().first { it.style == DocxParagraphStyle.HEADING_4 }
        assertEquals("Heading Four", h4.runs.first().text)

        val richPara = loaded.blocks.filterIsInstance<DocxBlock.Paragraph>().first { it.style == DocxParagraphStyle.MULTILEVEL }
        assertEquals(1, richPara.listLevel)
        val richRun = richPara.runs.first()
        assertEquals("Inter", richRun.fontFamily)
        assertTrue(richRun.strikethrough)
        assertEquals(com.example.domain.model.DocxScriptMode.SUPERSCRIPT, richRun.scriptMode)
        assertEquals("https://example.org", richRun.hyperlinkUrl)
        assertEquals(com.example.domain.model.DocxTrackChangeType.INSERTION, richRun.trackChangeType)

        val (replacedModel, replaceCount) = com.example.ui.viewmodel.DocxDocumentMutator.replaceAll(
            loaded,
            "Callout summary",
            "Updated Callout"
        )
        assertEquals(1, replaceCount)
        assertTrue(replacedModel.blocks.any { it is DocxBlock.TextBoxBlock && it.text == "Updated Callout" })
    }

    @Test
    fun `xlsx formula engine recalculates SUM AVERAGE IF and formats RM currency`() = runBlocking {
        val outFile = File(tempDir, "calc.xlsx")
        val sheet = XlsxWorksheet(
            sheetId = 1,
            name = "Budget",
            cells = mapOf(
                (0 to 0) to XlsxCell(0, 0, "2000", cachedValue = "2000", numberFormat = "RM #,##0.00"),
                (1 to 0) to XlsxCell(1, 0, "3000", cachedValue = "3000", numberFormat = "RM #,##0.00"),
                (2 to 0) to XlsxCell(2, 0, "=SUM(A1:A2)", formula = "SUM(A1:A2)", numberFormat = "RM #,##0.00"),
                (3 to 0) to XlsxCell(3, 0, "=AVERAGE(A1:A2)", formula = "AVERAGE(A1:A2)"),
                (4 to 0) to XlsxCell(4, 0, "=IF(A3>4000,1,0)", formula = "IF(A3>4000,1,0)")
            )
        )
        val wb = XlsxWorkbookModel("wb1", worksheets = listOf(sheet))
        XlsxEngine.saveWorkbook(wb, outFile)
        val loaded = XlsxEngine.loadWorkbook("wb1", outFile)
        val loadedSheet = loaded.worksheets.first()

        assertEquals("5000", loadedSheet.cells[2 to 0]?.cachedValue)
        assertEquals("RM 5,000.00", loadedSheet.cells[2 to 0]?.displayValue)
        assertEquals("2500", loadedSheet.cells[3 to 0]?.cachedValue)
        assertEquals("1", loadedSheet.cells[4 to 0]?.cachedValue)
        assertEquals("C12", "${SpreadsheetFormulaEngine.colIndexToName(2)}12")
    }

    @Test
    fun `pdf vector flattener appends lossless incremental update cleanly`() = runBlocking {
        val srcPdf = File(tempDir, "base.pdf")
        val outPdf = File(tempDir, "flattened.pdf")
        val stream = "BT /F1 14 Tf 72 720 Td (Original Vector Text) Tj ET\n"
        val objs = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length ${stream.length} >>\nstream\n$stream\nendstream\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objs.forEach { offsets.add(sb.length); sb.append(it) }
        val xrefOff = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { sb.append(String.format("%010d 00000 n \n", it)) }
        sb.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOff\n%%EOF\n")
        srcPdf.writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1))

        val annotations = listOf(
            PdfAnnotation(
                id = UUID.randomUUID().toString(),
                documentId = "p1",
                pageIndex = 0,
                type = PdfAnnotationType.HIGHLIGHT,
                colorArgb = 0xFFFACC15,
                bounds = NormalizedRect(0.1f, 0.1f, 0.5f, 0.14f)
            ),
            PdfAnnotation(
                id = UUID.randomUUID().toString(),
                documentId = "p1",
                pageIndex = 0,
                type = PdfAnnotationType.INK,
                colorArgb = 0xFF0F548C,
                points = listOf(
                    NormalizedPoint(0.2f, 0.3f),
                    NormalizedPoint(0.35f, 0.34f),
                    NormalizedPoint(0.5f, 0.31f)
                )
            ),
            PdfAnnotation(
                id = UUID.randomUUID().toString(),
                documentId = "p1",
                pageIndex = 0,
                type = PdfAnnotationType.TEXT_BOX,
                colorArgb = 0xFF0F172A,
                bounds = NormalizedRect(0.2f, 0.5f, 0.6f, 0.58f),
                textContent = "Verified Vector Note"
            )
        )

        val result = PdfVectorFlattener.flattenAnnotationsToFile(srcPdf, outPdf, annotations)
        assertTrue(result.isSuccess)
        val reparsed = PdfObjectStructure.parse(outPdf)
        assertNotNull(reparsed)
        assertEquals(1, reparsed!!.pages.size)
        // Original stream + appended vector overlay stream
        assertEquals(2, reparsed.pages.first().contentObjNums.size)
    }
}
