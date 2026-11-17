package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.ooxml.DocxEngine
import com.example.core.ooxml.XlsxEngine
import com.example.core.pdf.PdfObjectStructure
import com.example.core.pdf.PdfRenderEngine
import com.example.core.pdf.PdfVectorFlattener
import com.example.data.local.AnnotationDao
import com.example.data.local.AnnotationEntity
import com.example.data.local.AutosaveDao
import com.example.data.local.AutosaveEntity
import com.example.data.local.DocuDeskConverters
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfMetadataEntity
import com.example.data.local.SignatureDao
import com.example.data.local.SignatureEntity
import com.example.domain.model.CoreDocumentProperties
import com.example.domain.model.DocumentFormat
import com.example.domain.model.DocxAlignment
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxParagraphStyle
import com.example.domain.model.DocxTextRun
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.PdfAnnotation
import com.example.domain.model.SignaturePreset
import com.example.domain.model.SyncConflictState
import com.example.domain.model.XlsxCell
import com.example.domain.model.XlsxWorkbookModel
import com.example.domain.model.XlsxWorksheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val annotationDao: AnnotationDao,
    private val autosaveDao: AutosaveDao,
    private val signatureDao: SignatureDao,
    val pdfRenderEngine: PdfRenderEngine
) {
    private val workspaceDir: File
        get() = File(context.filesDir, "workspace").apply { mkdirs() }

    private val autosaveDir: File
        get() = File(context.filesDir, "autosaves").apply { mkdirs() }

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()

    val savedSignatures: Flow<List<SignaturePreset>> = signatureDao.observeSignatures().map { list ->
        list.map { entity ->
            SignaturePreset(
                id = entity.id,
                label = entity.label,
                strokes = DocuDeskConverters.jsonToStrokes(entity.strokesJson),
                colorArgb = entity.colorArgb,
                createdAt = entity.createdAt
            )
        }
    }

    fun observePdfMetadata(documentId: String): Flow<PdfMetadataEntity?> =
        documentDao.observePdfMetadata(documentId)

    fun observeAnnotations(documentId: String): Flow<List<PdfAnnotation>> =
        annotationDao.observeAnnotationsForDocument(documentId).map { entities ->
            entities.map { entity ->
                PdfAnnotation(
                    id = entity.id,
                    documentId = entity.documentId,
                    pageIndex = entity.pageIndex,
                    type = entity.type,
                    colorArgb = entity.colorArgb,
                    strokeWidthNormalized = entity.strokeWidthNormalized,
                    points = DocuDeskConverters.jsonToPoints(entity.pointsJson),
                    bounds = DocuDeskConverters.jsonToRect(entity.boundsJson),
                    textContent = entity.textContent,
                    fontSizeSp = entity.fontSizeSp,
                    author = entity.author,
                    createdAt = entity.createdAt,
                    isFlattened = entity.isFlattened
                )
            }
        }

    fun observeAutosaves(documentId: String): Flow<List<AutosaveEntity>> =
        autosaveDao.observeAutosaves(documentId)

    suspend fun ensureSeededWorkspace() = withContext(Dispatchers.IO) {
        val part1File = File(workspaceDir, "System_Analysis_Part_1.pdf")
        if (!part1File.exists()) {
            copyAssetIfPresent("samples/System_Analysis_Part_1.pdf", part1File)
            if (part1File.exists()) {
                indexPdfFile(
                    id = "seed_pdf_part1",
                    title = "M365 System Analysis & Feasibility — Part 1",
                    file = part1File,
                    department = "Platform Architecture",
                    classification = "Technical Report"
                )
            }
        }

        val part2File = File(workspaceDir, "System_Analysis_Part_2.pdf")
        if (!part2File.exists()) {
            copyAssetIfPresent("samples/System_Analysis_Part_2.pdf", part2File)
            if (part2File.exists()) {
                indexPdfFile(
                    id = "seed_pdf_part2",
                    title = "M365 System Analysis & Feasibility — Part 2",
                    file = part2File,
                    department = "Cloud & Security",
                    classification = "Technical Report"
                )
            }
        }

        val briefDocx = File(workspaceDir, "Architecture_Brief.docx")
        if (!briefDocx.exists()) {
            val docxModel = buildSeedDocx("seed_docx_brief")
            DocxEngine.saveDocument(docxModel, briefDocx)
            documentDao.upsertDocument(
                DocumentEntity(
                    id = "seed_docx_brief",
                    title = "Tablet Suite Architecture Brief",
                    fileName = briefDocx.name,
                    filePath = briefDocx.absolutePath,
                    format = DocumentFormat.DOCX,
                    fileSizeBytes = briefDocx.length(),
                    pageOrSheetCount = 2,
                    isPinnedOffline = true,
                    syncConflictState = SyncConflictState.CLEAN,
                    department = "Engineering",
                    classification = "Internal",
                    checksumSha256 = sha256(briefDocx),
                    lastOpenedAt = System.currentTimeMillis() - 10_000L
                )
            )
        }

        val matrixXlsx = File(workspaceDir, "Feasibility_Matrix.xlsx")
        if (!matrixXlsx.exists()) {
            val xlsxModel = buildSeedXlsx("seed_xlsx_matrix")
            XlsxEngine.saveWorkbook(xlsxModel, matrixXlsx)
            documentDao.upsertDocument(
                DocumentEntity(
                    id = "seed_xlsx_matrix",
                    title = "Platform Feasibility & Cost Matrix",
                    fileName = matrixXlsx.name,
                    filePath = matrixXlsx.absolutePath,
                    format = DocumentFormat.XLSX,
                    fileSizeBytes = matrixXlsx.length(),
                    pageOrSheetCount = xlsxModel.worksheets.size,
                    isPinnedOffline = true,
                    syncConflictState = SyncConflictState.CLEAN,
                    department = "Finance & Ops",
                    classification = "Confidential",
                    checksumSha256 = sha256(matrixXlsx),
                    lastOpenedAt = System.currentTimeMillis() - 20_000L
                )
            )
        }
    }

    suspend fun importFromUri(uri: Uri): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "Imported_${System.currentTimeMillis()}.pdf"
            val ext = displayName.substringAfterLast('.', "pdf").lowercase()
            val format = DocumentFormat.fromExtension(ext)
            val safeName = "${UUID.randomUUID().toString().take(8)}_${displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
            val destFile = File(workspaceDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to read selected file.")

            val id = UUID.randomUUID().toString()
            val title = displayName.substringBeforeLast('.')
            when (format) {
                DocumentFormat.PDF -> indexPdfFile(id, title, destFile, "Imported", "User Document")
                DocumentFormat.DOCX -> {
                    val model = DocxEngine.loadDocument(id, destFile)
                    val entity = DocumentEntity(
                        id = id,
                        title = model.coreProperties.title.ifBlank { title },
                        fileName = destFile.name,
                        filePath = destFile.absolutePath,
                        format = DocumentFormat.DOCX,
                        fileSizeBytes = destFile.length(),
                        pageOrSheetCount = maxOf(1, (model.blocks.size / 12) + 1),
                        checksumSha256 = sha256(destFile)
                    )
                    documentDao.upsertDocument(entity)
                    entity
                }
                DocumentFormat.XLSX -> {
                    val wb = XlsxEngine.loadWorkbook(id, destFile)
                    val entity = DocumentEntity(
                        id = id,
                        title = wb.coreProperties.title.ifBlank { title },
                        fileName = destFile.name,
                        filePath = destFile.absolutePath,
                        format = DocumentFormat.XLSX,
                        fileSizeBytes = destFile.length(),
                        pageOrSheetCount = wb.worksheets.size,
                        checksumSha256 = sha256(destFile)
                    )
                    documentDao.upsertDocument(entity)
                    entity
                }
            }
        }
    }

    suspend fun copyImageUriToMediaFile(documentId: String, uri: Uri): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaDir = File(context.filesDir, "workspace_media/${documentId.take(8)}").apply { mkdirs() }
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("webp") -> "webp"
                    else -> "png"
                }
                val fileName = "img_${System.currentTimeMillis()}.$ext"
                val dest = File(mediaDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Unable to read selected image.")
                dest
            }
        }

    suspend fun createNewDocument(format: DocumentFormat, title: String): DocumentEntity =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val safeBase = title.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Document" }
            val file = File(workspaceDir, "${safeBase}_${id.take(6)}.${format.extension}")
            val now = System.currentTimeMillis()

            val pageOrSheetCount = when (format) {
                DocumentFormat.DOCX -> {
                    val model = DocxDocumentModel(
                        documentId = id,
                        coreProperties = CoreDocumentProperties(title = title),
                        blocks = listOf(
                            DocxBlock.Paragraph(
                                id = UUID.randomUUID().toString(),
                                style = DocxParagraphStyle.HEADING_1,
                                runs = listOf(DocxTextRun(UUID.randomUUID().toString(), title, bold = true, fontSizeSp = 22f))
                            ),
                            DocxBlock.Paragraph(
                                id = UUID.randomUUID().toString(),
                                style = DocxParagraphStyle.NORMAL,
                                runs = listOf(
                                    DocxTextRun(
                                        UUID.randomUUID().toString(),
                                        "Start typing with your physical keyboard or edit block styles from the desktop Ribbon."
                                    )
                                )
                            )
                        )
                    )
                    DocxEngine.saveDocument(model, file)
                    1
                }
                DocumentFormat.XLSX -> {
                    val wb = XlsxWorkbookModel(
                        documentId = id,
                        coreProperties = CoreDocumentProperties(title = title),
                        worksheets = listOf(
                            XlsxWorksheet(
                                sheetId = 1,
                                name = "Sheet1",
                                cells = mapOf(
                                    (0 to 0) to XlsxCell(0, 0, "Item", cachedValue = "Item", displayValue = "Item", isBold = true),
                                    (0 to 1) to XlsxCell(0, 1, "Budget (RM)", cachedValue = "Budget (RM)", displayValue = "Budget (RM)", isBold = true),
                                    (1 to 0) to XlsxCell(1, 0, "Module A", cachedValue = "Module A", displayValue = "Module A"),
                                    (1 to 1) to XlsxCell(1, 1, "2500", cachedValue = "2500", displayValue = "RM 2,500.00", numberFormat = "RM #,##0.00"),
                                    (2 to 0) to XlsxCell(2, 0, "Total", cachedValue = "Total", displayValue = "Total", isBold = true),
                                    (2 to 1) to XlsxCell(2, 1, "=SUM(B2:B2)", formula = "SUM(B2:B2)", cachedValue = "2500", displayValue = "RM 2,500.00", numberFormat = "RM #,##0.00", isBold = true)
                                )
                            )
                        )
                    )
                    XlsxEngine.saveWorkbook(wb, file)
                    1
                }
                DocumentFormat.PDF -> {
                    // Create a clean blank vector PDF
                    val blankPdf = buildBlankVectorPdf(title)
                    file.writeBytes(blankPdf)
                    1
                }
            }

            val entity = DocumentEntity(
                id = id,
                title = title,
                fileName = file.name,
                filePath = file.absolutePath,
                format = format,
                fileSizeBytes = file.length(),
                pageOrSheetCount = pageOrSheetCount,
                checksumSha256 = sha256(file),
                lastOpenedAt = now,
                lastModifiedAt = now
            )
            documentDao.upsertDocument(entity)
            if (format == DocumentFormat.PDF) {
                documentDao.upsertPdfMetadata(
                    PdfMetadataEntity(
                        documentId = id,
                        pdfVersion = "1.4",
                        pageCount = 1,
                        title = title,
                        creator = "DocuDesk"
                    )
                )
            }
            entity
        }

    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getDocumentById(id)

    suspend fun touchLastOpened(id: String) =
        documentDao.touchLastOpened(id, System.currentTimeMillis())

    suspend fun updatePdfViewport(documentId: String, page: Int, zoom: Float) =
        documentDao.updatePdfViewport(documentId, page, zoom)

    suspend fun toggleOfflinePin(documentId: String, pinned: Boolean) =
        documentDao.updatePinnedOffline(documentId, pinned)

    suspend fun saveAnnotation(annotation: PdfAnnotation) {
        annotationDao.upsertAnnotation(annotation.toEntity())
        documentDao.updateSyncState(
            annotation.documentId,
            SyncConflictState.DIRTY_LOCAL,
            System.currentTimeMillis()
        )
    }

    suspend fun deleteAnnotation(documentId: String, annotationId: String) {
        annotationDao.deleteAnnotationById(annotationId)
        documentDao.updateSyncState(
            documentId,
            SyncConflictState.DIRTY_LOCAL,
            System.currentTimeMillis()
        )
    }

    suspend fun saveSignaturePreset(label: String, strokes: List<List<NormalizedPoint>>, colorArgb: Long) {
        signatureDao.insertSignature(
            SignatureEntity(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { "Signature" },
                strokesJson = DocuDeskConverters.strokesToJson(strokes),
                colorArgb = colorArgb,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSignaturePreset(id: String) = signatureDao.deleteSignature(id)

    suspend fun createAutosaveSnapshot(documentId: String, summary: String) =
        withContext(Dispatchers.IO) {
            val doc = documentDao.getDocumentById(documentId) ?: return@withContext
            val src = File(doc.filePath)
            if (!src.exists()) return@withContext
            val snapId = UUID.randomUUID().toString()
            val snapFile = File(autosaveDir, "${documentId}_${snapId.take(8)}.${doc.format.extension}")
            src.copyTo(snapFile, overwrite = true)
            autosaveDao.insertAutosave(
                AutosaveEntity(
                    id = snapId,
                    documentId = documentId,
                    snapshotPath = snapFile.absolutePath,
                    summary = summary,
                    byteSize = snapFile.length(),
                    createdAt = System.currentTimeMillis()
                )
            )
            autosaveDao.pruneOldAutosaves(documentId)
        }

    suspend fun flattenAndSavePdf(documentId: String, destinationUri: Uri? = null): Result<File> =
        withContext(Dispatchers.IO) {
            val doc = documentDao.getDocumentById(documentId)
                ?: return@withContext Result.failure(IllegalArgumentException("Document not found"))
            val sourceFile = File(doc.filePath)
            val annotations = annotationDao.getUnflattenedAnnotations(documentId).map { entity ->
                PdfAnnotation(
                    id = entity.id,
                    documentId = entity.documentId,
                    pageIndex = entity.pageIndex,
                    type = entity.type,
                    colorArgb = entity.colorArgb,
                    strokeWidthNormalized = entity.strokeWidthNormalized,
                    points = DocuDeskConverters.jsonToPoints(entity.pointsJson),
                    bounds = DocuDeskConverters.jsonToRect(entity.boundsJson),
                    textContent = entity.textContent,
                    fontSizeSp = entity.fontSizeSp,
                    author = entity.author,
                    createdAt = entity.createdAt,
                    isFlattened = entity.isFlattened
                )
            }

            val result = PdfVectorFlattener.flattenAnnotationsToFile(
                sourceFile = sourceFile,
                destinationFile = sourceFile,
                annotations = annotations
            )

            if (result.isSuccess) {
                annotationDao.markAllFlattened(documentId)
                pdfRenderEngine.invalidateDocumentCache(documentId)
                documentDao.upsertDocument(
                    doc.copy(
                        fileSizeBytes = sourceFile.length(),
                        checksumSha256 = sha256(sourceFile),
                        syncConflictState = SyncConflictState.CLEAN,
                        lastModifiedAt = System.currentTimeMillis()
                    )
                )
                createAutosaveSnapshot(documentId, "Flattened ${annotations.size} vector annotations")
                if (destinationUri != null) {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }
            }
            result
        }

    suspend fun saveDocx(model: DocxDocumentModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = documentDao.getDocumentById(model.documentId)
                ?: throw IllegalArgumentException("Document missing")
            val file = File(doc.filePath)
            DocxEngine.saveDocument(model, file)
            documentDao.upsertDocument(
                doc.copy(
                    title = model.coreProperties.title.ifBlank { doc.title },
                    fileSizeBytes = file.length(),
                    pageOrSheetCount = maxOf(1, (model.blocks.size / 10) + 1),
                    checksumSha256 = sha256(file),
                    syncConflictState = SyncConflictState.CLEAN,
                    lastModifiedAt = System.currentTimeMillis()
                )
            )
            createAutosaveSnapshot(model.documentId, "Saved WordprocessingML package")
        }
    }

    suspend fun saveXlsx(model: XlsxWorkbookModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = documentDao.getDocumentById(model.documentId)
                ?: throw IllegalArgumentException("Workbook missing")
            val file = File(doc.filePath)
            XlsxEngine.saveWorkbook(model, file)
            documentDao.upsertDocument(
                doc.copy(
                    title = model.coreProperties.title.ifBlank { doc.title },
                    fileSizeBytes = file.length(),
                    pageOrSheetCount = model.worksheets.size,
                    checksumSha256 = sha256(file),
                    syncConflictState = SyncConflictState.CLEAN,
                    lastModifiedAt = System.currentTimeMillis()
                )
            )
            createAutosaveSnapshot(model.documentId, "Saved SpreadsheetML workbook")
        }
    }

    suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId)
        if (doc != null) {
            runCatching { File(doc.filePath).delete() }
            pdfRenderEngine.invalidateDocumentCache(documentId)
            documentDao.deleteDocumentById(documentId)
        }
    }

    private suspend fun indexPdfFile(
        id: String,
        title: String,
        file: File,
        department: String,
        classification: String
    ): DocumentEntity {
        val dims = runCatching { pdfRenderEngine.openDocument(file) }.getOrDefault(emptyList())
        val parsed = runCatching { PdfObjectStructure.parse(file) }.getOrNull()
        val pageCount = maxOf(dims.size, parsed?.pages?.size ?: 1)
        val docEntity = DocumentEntity(
            id = id,
            title = parsed?.title?.takeIf { it.isNotBlank() } ?: title,
            fileName = file.name,
            filePath = file.absolutePath,
            format = DocumentFormat.PDF,
            fileSizeBytes = file.length(),
            pageOrSheetCount = pageCount,
            isPinnedOffline = true,
            syncConflictState = SyncConflictState.CLEAN,
            department = department,
            classification = classification,
            checksumSha256 = sha256(file)
        )
        documentDao.upsertDocument(docEntity)
        documentDao.upsertPdfMetadata(
            PdfMetadataEntity(
                documentId = id,
                pdfVersion = parsed?.pdfVersion ?: "1.7",
                pageCount = pageCount,
                title = docEntity.title,
                author = parsed?.author ?: "",
                subject = parsed?.subject ?: "",
                creator = parsed?.creator ?: "PDF Engine"
            )
        )
        return docEntity
    }

    private fun copyAssetIfPresent(assetPath: String, destination: File) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
        }
    }

    private fun sha256(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read = input.read(buf)
                while (read > 0) {
                    digest.update(buf, 0, read)
                    read = input.read(buf)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun PdfAnnotation.toEntity() = AnnotationEntity(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        type = type,
        colorArgb = colorArgb,
        strokeWidthNormalized = strokeWidthNormalized,
        pointsJson = DocuDeskConverters.pointsToJson(points),
        boundsJson = DocuDeskConverters.rectToJson(bounds),
        textContent = textContent,
        fontSizeSp = fontSizeSp,
        author = author,
        createdAt = createdAt,
        isFlattened = isFlattened
    )

    private fun buildBlankVectorPdf(title: String): ByteArray {
        val stream = "BT /F1 18 Tf 72 720 Td ($title) Tj ET\n"
        val objs = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length ${stream.length} >>\nstream\n$stream\nendstream\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objs.forEach { obj ->
            offsets.add(sb.length)
            sb.append(obj)
        }
        val xrefOffset = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off ->
            sb.append(String.format(java.util.Locale.US, "%010d 00000 n \n", off))
        }
        sb.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun buildSeedDocx(docId: String): DocxDocumentModel {
        return DocxDocumentModel(
            documentId = docId,
            coreProperties = CoreDocumentProperties(
                title = "Tablet Suite Architecture Brief",
                creator = "Principal Systems Architect",
                subject = "OOXML & Desktop Tablet Ergonomics",
                department = "Engineering",
                classification = "Technical Spec"
            ),
            blocks = listOf(
                DocxBlock.Paragraph(
                    id = UUID.randomUUID().toString(),
                    style = DocxParagraphStyle.HEADING_1,
                    runs = listOf(
                        DocxTextRun(
                            UUID.randomUUID().toString(),
                            "Microsoft 365 Ecosystem & Tablet Architecture Brief",
                            bold = true,
                            fontSizeSp = 22f
                        )
                    )
                ),
                DocxBlock.Paragraph(
                    id = UUID.randomUUID().toString(),
                    style = DocxParagraphStyle.NORMAL,
                    runs = listOf(
                        DocxTextRun(
                            UUID.randomUUID().toString(),
                            "Grounded in Part 1 & Part 2 of the System Analysis and Feasibility Report, this workspace implements Open Packaging Conventions (OPC), WordprocessingML, SpreadsheetML formula recalculation, and lossless vector PDF annotation flattening."
                        )
                    )
                ),
                DocxBlock.Paragraph(
                    id = UUID.randomUUID().toString(),
                    style = DocxParagraphStyle.HEADING_2,
                    runs = listOf(
                        DocxTextRun(
                            UUID.randomUUID().toString(),
                            "Core Architectural Layers",
                            bold = true,
                            fontSizeSp = 17f
                        )
                    )
                ),
                DocxBlock.Table(
                    id = UUID.randomUUID().toString(),
                    rows = listOf(
                        listOf("Layer", "Implementation", "Optimization"),
                        listOf("Presentation", "Fluent 2 Ribbon + Multi-Tab Shell", "144Hz Compose + Keyboard Shortcuts"),
                        listOf("OOXML Engine", "XmlPullParser Streaming + ZIP OPC", "Low memory DOM & SharedStrings"),
                        listOf("PDF Engine", "PdfRenderer + Vector Flattener", "LruCache + Stylus Ink Bézier"),
                        listOf("Local Continuity", "Room DB + Autosave Snapshots", "Offline Pinning + SHA-256")
                    )
                ),
                DocxBlock.Paragraph(
                    id = UUID.randomUUID().toString(),
                    style = DocxParagraphStyle.QUOTE,
                    runs = listOf(
                        DocxTextRun(
                            UUID.randomUUID().toString(),
                            "\"Local client state is a performance and continuity layer; combine resilient offline workflows with deterministic recovery snapshots.\"",
                            italic = true
                        )
                    )
                )
            )
        )
    }

    private fun buildSeedXlsx(docId: String): XlsxWorkbookModel {
        val sheet1Cells = linkedMapOf(
            (0 to 0) to XlsxCell(0, 0, "Workstream", cachedValue = "Workstream", displayValue = "Workstream", isBold = true),
            (0 to 1) to XlsxCell(0, 1, "Q1 Allocation", cachedValue = "Q1 Allocation", displayValue = "Q1 Allocation", isBold = true),
            (0 to 2) to XlsxCell(0, 2, "Q2 Allocation", cachedValue = "Q2 Allocation", displayValue = "Q2 Allocation", isBold = true),
            (0 to 3) to XlsxCell(0, 3, "Total Budget", cachedValue = "Total Budget", displayValue = "Total Budget", isBold = true),
            (0 to 4) to XlsxCell(0, 4, "Readiness", cachedValue = "Readiness", displayValue = "Readiness", isBold = true),

            (1 to 0) to XlsxCell(1, 0, "Identity & Conditional Access", cachedValue = "Identity & Conditional Access", displayValue = "Identity & Conditional Access"),
            (1 to 1) to XlsxCell(1, 1, "5000", cachedValue = "5000", displayValue = "RM 5,000.00", numberFormat = "RM #,##0.00"),
            (1 to 2) to XlsxCell(1, 2, "6200", cachedValue = "6200", displayValue = "RM 6,200.00", numberFormat = "RM #,##0.00"),
            (1 to 3) to XlsxCell(1, 3, "=SUM(B2:C2)", formula = "SUM(B2:C2)", cachedValue = "11200", displayValue = "RM 11,200.00", numberFormat = "RM #,##0.00", isBold = true),
            (1 to 4) to XlsxCell(1, 4, "=IF(D2>10000,1,0)", formula = "IF(D2>10000,1,0)", cachedValue = "1", displayValue = "1"),

            (2 to 0) to XlsxCell(2, 0, "PDF & OOXML Tablet Client", cachedValue = "PDF & OOXML Tablet Client", displayValue = "PDF & OOXML Tablet Client"),
            (2 to 1) to XlsxCell(2, 1, "8400", cachedValue = "8400", displayValue = "RM 8,400.00", numberFormat = "RM #,##0.00"),
            (2 to 2) to XlsxCell(2, 2, "9100", cachedValue = "9100", displayValue = "RM 9,100.00", numberFormat = "RM #,##0.00"),
            (2 to 3) to XlsxCell(2, 3, "=SUM(B3:C3)", formula = "SUM(B3:C3)", cachedValue = "17500", displayValue = "RM 17,500.00", numberFormat = "RM #,##0.00", isBold = true),
            (2 to 4) to XlsxCell(2, 4, "=IF(D3>10000,1,0)", formula = "IF(D3>10000,1,0)", cachedValue = "1", displayValue = "1"),

            (3 to 0) to XlsxCell(3, 0, "Backup & Resilience", cachedValue = "Backup & Resilience", displayValue = "Backup & Resilience"),
            (3 to 1) to XlsxCell(3, 1, "4200", cachedValue = "4200", displayValue = "RM 4,200.00", numberFormat = "RM #,##0.00"),
            (3 to 2) to XlsxCell(3, 2, "4800", cachedValue = "4800", displayValue = "RM 4,800.00", numberFormat = "RM #,##0.00"),
            (3 to 3) to XlsxCell(3, 3, "=SUM(B4:C4)", formula = "SUM(B4:C4)", cachedValue = "9000", displayValue = "RM 9,000.00", numberFormat = "RM #,##0.00", isBold = true),
            (3 to 4) to XlsxCell(3, 4, "=IF(D4>10000,1,0)", formula = "IF(D4>10000,1,0)", cachedValue = "0", displayValue = "0"),

            (5 to 0) to XlsxCell(5, 0, "Portfolio Total", cachedValue = "Portfolio Total", displayValue = "Portfolio Total", isBold = true),
            (5 to 1) to XlsxCell(5, 1, "=SUM(B2:B4)", formula = "SUM(B2:B4)", cachedValue = "17600", displayValue = "RM 17,600.00", numberFormat = "RM #,##0.00", isBold = true),
            (5 to 2) to XlsxCell(5, 2, "=SUM(C2:C4)", formula = "SUM(C2:C4)", cachedValue = "20100", displayValue = "RM 20,100.00", numberFormat = "RM #,##0.00", isBold = true),
            (5 to 3) to XlsxCell(5, 3, "=SUM(D2:D4)", formula = "SUM(D2:D4)", cachedValue = "37700", displayValue = "RM 37,700.00", numberFormat = "RM #,##0.00", isBold = true),
            (5 to 4) to XlsxCell(5, 4, "=AVERAGE(D2:D4)", formula = "AVERAGE(D2:D4)", cachedValue = "12566.6667", displayValue = "RM 12,566.67", numberFormat = "RM #,##0.00", isBold = true)
        )

        val sheet2Cells = linkedMapOf(
            (0 to 0) to XlsxCell(0, 0, "Risk Item", cachedValue = "Risk Item", displayValue = "Risk Item", isBold = true),
            (0 to 1) to XlsxCell(0, 1, "Impact Score", cachedValue = "Impact Score", displayValue = "Impact Score", isBold = true),
            (1 to 0) to XlsxCell(1, 0, "Identity Compromise", cachedValue = "Identity Compromise", displayValue = "Identity Compromise"),
            (1 to 1) to XlsxCell(1, 1, "9", cachedValue = "9", displayValue = "9"),
            (2 to 0) to XlsxCell(2, 0, "Sync Conflicts", cachedValue = "Sync Conflicts", displayValue = "Sync Conflicts"),
            (2 to 1) to XlsxCell(2, 1, "6", cachedValue = "6", displayValue = "6"),
            (3 to 0) to XlsxCell(3, 0, "Max Risk", cachedValue = "Max Risk", displayValue = "Max Risk", isBold = true),
            (3 to 1) to XlsxCell(3, 1, "=MAX(B2:B3)", formula = "MAX(B2:B3)", cachedValue = "9", displayValue = "9", isBold = true)
        )

        return XlsxWorkbookModel(
            documentId = docId,
            coreProperties = CoreDocumentProperties(
                title = "Platform Feasibility & Cost Matrix",
                creator = "DocuDesk Architecture Team",
                subject = "Part 2 Section 8 & 9 Feasibility Model",
                department = "Finance & Ops",
                classification = "Confidential"
            ),
            worksheets = listOf(
                XlsxWorksheet(sheetId = 1, name = "Feasibility Budget", cells = sheet1Cells),
                XlsxWorksheet(sheetId = 2, name = "Risk Register", cells = sheet2Cells)
            )
        )
    }
}
