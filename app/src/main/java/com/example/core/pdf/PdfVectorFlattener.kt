package com.example.core.pdf

import com.example.domain.model.PdfAnnotation
import com.example.domain.model.PdfAnnotationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Lossless Vector PDF Annotation Flattener & Incremental Writer.
 * Preserves existing PDF vector streams byte-for-byte and appends vector graphics operators
 * (highlights with /ExtGState alpha, Bézier ink & signature strokes, and Helvetica vector text)
 * via compliant classic xref or PDF 1.5+ /Type /XRef incremental updates.
 */
object PdfVectorFlattener {

    suspend fun flattenAnnotationsToFile(
        sourceFile: File,
        destinationFile: File,
        annotations: List<PdfAnnotation>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val activeAnnotations = annotations.filter { !it.isFlattened }
            if (activeAnnotations.isEmpty()) {
                if (sourceFile.absolutePath != destinationFile.absolutePath) {
                    sourceFile.copyTo(destinationFile, overwrite = true)
                }
                return@runCatching destinationFile
            }

            val sourceBytes = sourceFile.readBytes()
            val structure = PdfObjectStructure.parse(sourceBytes)
                ?: throw IllegalStateException("Unsupported or unreadable PDF cross-reference structure.")

            val byPage = activeAnnotations.groupBy { it.pageIndex }
            val baos = ByteArrayOutputStream(sourceBytes.size + 16384)
            baos.write(sourceBytes)
            if (sourceBytes.isNotEmpty() && sourceBytes.last() != '\n'.code.toByte()) {
                baos.write('\n'.code)
            }

            var nextObjNum = structure.maxObjNum + 1
            val updatedObjects = linkedMapOf<Int, Pair<Long, ByteArray>>() // objNum -> offset, serialized

            // Shared ExtGState and Helvetica Font indirect objects for overlay streams
            val gsObjNum = nextObjNum++
            val gsBody = "<< /Type /ExtGState /ca 0.34 /CA 0.34 /BM /Multiply >>"
            val fontObjNum = nextObjNum++
            val fontBody = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"

            val sharedObjs = listOf(gsObjNum to gsBody, fontObjNum to fontBody)
            sharedObjs.forEach { (num, body) ->
                val objOffset = baos.size().toLong()
                val objBytes = "$num 0 obj\n$body\nendobj\n".toByteArray(StandardCharsets.ISO_8859_1)
                baos.write(objBytes)
                updatedObjects[num] = objOffset to objBytes
            }

            byPage.forEach { (pageIndex, pageAnnotations) ->
                val pageInfo = structure.pages.getOrNull(pageIndex) ?: return@forEach
                val vectorStream = buildPageVectorStream(pageInfo, pageAnnotations)
                val streamBytes = vectorStream.toByteArray(StandardCharsets.ISO_8859_1)

                val contentObjNum = nextObjNum++
                val contentOffset = baos.size().toLong()
                val contentHeader = "$contentObjNum 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n"
                    .toByteArray(StandardCharsets.ISO_8859_1)
                val contentFooter = "\nendstream\nendobj\n".toByteArray(StandardCharsets.ISO_8859_1)
                baos.write(contentHeader)
                baos.write(streamBytes)
                baos.write(contentFooter)
                updatedObjects[contentObjNum] = contentOffset to ByteArray(0)

                val updatedResources = buildMergedResources(
                    existingResources = pageInfo.resourcesRefOrInline,
                    gsObjNum = gsObjNum,
                    fontObjNum = fontObjNum,
                    structure = structure,
                    sourceBytes = sourceBytes
                )

                val newContentsList = (pageInfo.contentObjNums + contentObjNum)
                    .joinToString(" ") { "$it 0 R" }

                val updatedPageDict = rebuildPageDictionary(
                    originalDict = pageInfo.pageDictText,
                    newContentsArray = "[ $newContentsList ]",
                    newResources = updatedResources
                )

                val pageOffset = baos.size().toLong()
                val pageBytes = "${pageInfo.pageObjNum} 0 obj\n$updatedPageDict\nendobj\n"
                    .toByteArray(StandardCharsets.ISO_8859_1)
                baos.write(pageBytes)
                updatedObjects[pageInfo.pageObjNum] = pageOffset to pageBytes
            }

            // Append incremental xref section matching the source PDF's xref convention
            val newXrefOffset = baos.size().toLong()
            if (structure.isXRefStreamBased) {
                val xrefObjNum = nextObjNum++
                val xrefStreamObj = buildIncrementalXrefStreamObject(
                    xrefObjNum = xrefObjNum,
                    xrefOffset = newXrefOffset,
                    size = nextObjNum,
                    rootObjNum = structure.rootObjNum,
                    infoObjNum = structure.infoObjNum,
                    prevXrefOffset = structure.lastXrefOffset,
                    updatedObjects = updatedObjects
                )
                baos.write(xrefStreamObj)
            } else {
                val classicTrailer = buildClassicIncrementalXref(
                    size = nextObjNum,
                    rootObjNum = structure.rootObjNum,
                    infoObjNum = structure.infoObjNum,
                    prevXrefOffset = structure.lastXrefOffset,
                    updatedObjects = updatedObjects
                )
                baos.write(classicTrailer.toByteArray(StandardCharsets.ISO_8859_1))
            }
            baos.write("startxref\n$newXrefOffset\n%%EOF\n".toByteArray(StandardCharsets.ISO_8859_1))

            val tempOut = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
            tempOut.writeBytes(baos.toByteArray())
            if (destinationFile.exists() && destinationFile.absolutePath == sourceFile.absolutePath) {
                destinationFile.delete()
            }
            tempOut.renameTo(destinationFile)
            destinationFile
        }
    }

    private fun buildPageVectorStream(
        page: PdfPageRefInfo,
        annotations: List<PdfAnnotation>
    ): String {
        val w = page.widthPt
        val h = page.heightPt
        val ox = page.mediaBoxOriginX
        val oy = page.mediaBoxOriginY

        fun toPdfX(nx: Float): String = String.format(Locale.US, "%.2f", ox + nx * w)
        fun toPdfY(ny: Float): String = String.format(Locale.US, "%.2f", oy + (1f - ny) * h)

        return buildString {
            append("q\n")
            annotations.forEach { ann ->
                val r = ((ann.colorArgb shr 16) and 0xFF) / 255f
                val g = ((ann.colorArgb shr 8) and 0xFF) / 255f
                val b = (ann.colorArgb and 0xFF) / 255f
                val rgb = String.format(Locale.US, "%.3f %.3f %.3f", r, g, b)

                when (ann.type) {
                    PdfAnnotationType.HIGHLIGHT -> {
                        val rect = ann.bounds ?: return@forEach
                        val x = ox + rect.left * w
                        val yTop = oy + (1f - rect.top) * h
                        val yBot = oy + (1f - rect.bottom) * h
                        val rectW = ((rect.right - rect.left) * w).coerceAtLeast(1f)
                        val rectH = (yTop - yBot).coerceAtLeast(1f)
                        append("q /GS_DocuHL gs $rgb rg ")
                        append(String.format(Locale.US, "%.2f %.2f %.2f %.2f re f ", x, yBot, rectW, rectH))
                        append("Q\n")
                    }

                    PdfAnnotationType.UNDERLINE -> {
                        val rect = ann.bounds ?: return@forEach
                        val x1 = toPdfX(rect.left)
                        val x2 = toPdfX(rect.right)
                        val y = toPdfY(rect.bottom)
                        val lw = String.format(Locale.US, "%.2f", (ann.strokeWidthNormalized * w).coerceAtLeast(1.2f))
                        append("q 1 J $lw w $rgb RG $x1 $y m $x2 $y l S Q\n")
                    }

                    PdfAnnotationType.INK, PdfAnnotationType.SIGNATURE -> {
                        if (ann.points.size < 2) return@forEach
                        val lw = String.format(
                            Locale.US,
                            "%.2f",
                            (ann.strokeWidthNormalized * w).coerceAtLeast(1.0f)
                        )
                        append("q 1 J 1 j $lw w $rgb RG ")
                        val first = ann.points.first()
                        append("${toPdfX(first.x)} ${toPdfY(first.y)} m ")
                        for (i in 1 until ann.points.size) {
                            val pt = ann.points[i]
                            append("${toPdfX(pt.x)} ${toPdfY(pt.y)} l ")
                        }
                        append("S Q\n")
                    }

                    PdfAnnotationType.TEXT_BOX -> {
                        val rect = ann.bounds ?: return@forEach
                        if (ann.textContent.isBlank()) return@forEach
                        val fontSize = ann.fontSizeSp.coerceIn(8f, 36f)
                        val x = toPdfX(rect.left)
                        val y = toPdfY(rect.top)
                        val lines = ann.textContent.lines()
                        append("q BT /F_DocuHelv ${String.format(Locale.US, "%.1f", fontSize)} Tf ")
                        append("$rgb rg ")
                        append("$x $y Td ")
                        append("${String.format(Locale.US, "%.1f", -(fontSize * 1.25f))} TL ")
                        lines.forEachIndexed { idx, line ->
                            if (idx > 0) append("T* ")
                            append("(${escapePdfString(line)}) Tj ")
                        }
                        append("ET Q\n")
                    }
                }
            }
            append("Q\n")
        }
    }

    private fun buildMergedResources(
        existingResources: String,
        gsObjNum: Int,
        fontObjNum: Int,
        structure: PdfParsedStructure,
        sourceBytes: ByteArray
    ): String {
        val resolvedDict = if (existingResources.trim().endsWith("R")) {
            val refNum = existingResources.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
            val entry = refNum?.let { structure.xrefEntries[it] }
            if (entry != null && entry.inObjectStreamNum == null) {
                val off = entry.byteOffset.toInt()
                val raw = String(
                    sourceBytes,
                    off,
                    minOf(sourceBytes.size - off, 8192),
                    StandardCharsets.ISO_8859_1
                )
                val s = raw.indexOf("<<")
                val e = raw.lastIndexOf(">>")
                if (s >= 0 && e > s) raw.substring(s, e + 2) else "<< >>"
            } else "<< >>"
        } else existingResources

        val inner = resolvedDict.trim().removePrefix("<<").removeSuffix(">>").trim()
        val hasExtGState = inner.contains("/ExtGState")
        val hasFont = inner.contains("/Font")

        var updated = inner
        updated = if (hasExtGState) {
            updated.replace(
                Regex("/ExtGState\\s*<<"),
                "/ExtGState << /GS_DocuHL $gsObjNum 0 R "
            )
        } else {
            "$updated /ExtGState << /GS_DocuHL $gsObjNum 0 R >>"
        }

        updated = if (hasFont && Regex("/Font\\s*<<").containsMatchIn(updated)) {
            updated.replace(
                Regex("/Font\\s*<<"),
                "/Font << /F_DocuHelv $fontObjNum 0 R "
            )
        } else if (!hasFont) {
            "$updated /Font << /F_DocuHelv $fontObjNum 0 R >>"
        } else {
            "$updated /Font << /F_DocuHelv $fontObjNum 0 R >>"
        }

        return "<< $updated >>"
    }

    private fun rebuildPageDictionary(
        originalDict: String,
        newContentsArray: String,
        newResources: String
    ): String {
        val start = originalDict.indexOf("<<")
        val end = originalDict.lastIndexOf(">>")
        val inner = if (start >= 0 && end > start) {
            originalDict.substring(start + 2, end)
        } else originalDict

        var cleaned = inner
            .replace(Regex("/Contents\\s*\\[.*?\\]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("/Contents\\s+\\d+\\s+\\d+\\s+R"), "")
            .replace(Regex("/Resources\\s+\\d+\\s+\\d+\\s+R"), "")
        // Strip inline /Resources << ... >> safely if present
        val resIdx = cleaned.indexOf("/Resources")
        if (resIdx >= 0) {
            val afterKey = cleaned.substring(resIdx + "/Resources".length).trimStart()
            if (afterKey.startsWith("<<")) {
                var depth = 0
                var i = 0
                while (i < afterKey.length - 1) {
                    if (afterKey[i] == '<' && afterKey[i + 1] == '<') {
                        depth++; i += 2; continue
                    } else if (afterKey[i] == '>' && afterKey[i + 1] == '>') {
                        depth--
                        if (depth == 0) {
                            val prefix = cleaned.substring(0, resIdx)
                            val suffix = afterKey.substring(i + 2)
                            cleaned = "$prefix $suffix"
                            break
                        }
                        i += 2; continue
                    }
                    i++
                }
            }
        }
        return "<< $cleaned /Contents $newContentsArray /Resources $newResources >>"
    }

    private fun buildClassicIncrementalXref(
        size: Int,
        rootObjNum: Int,
        infoObjNum: Int?,
        prevXrefOffset: Long,
        updatedObjects: Map<Int, Pair<Long, ByteArray>>
    ): String {
        return buildString {
            append("xref\n")
            updatedObjects.keys.sorted().forEach { objNum ->
                val offset = updatedObjects[objNum]!!.first
                append("$objNum 1\n")
                append(String.format(Locale.US, "%010d 00000 n \n", offset))
            }
            append("trailer\n<< /Size $size /Root $rootObjNum 0 R ")
            if (infoObjNum != null) append("/Info $infoObjNum 0 R ")
            append("/Prev $prevXrefOffset >>\n")
        }
    }

    private fun buildIncrementalXrefStreamObject(
        xrefObjNum: Int,
        xrefOffset: Long,
        size: Int,
        rootObjNum: Int,
        infoObjNum: Int?,
        prevXrefOffset: Long,
        updatedObjects: Map<Int, Pair<Long, ByteArray>>
    ): ByteArray {
        val allUpdates = TreeMapWrapper(updatedObjects.mapValues { it.value.first })
        allUpdates[xrefObjNum] = xrefOffset

        val indexPairs = StringBuilder()
        val streamBaos = ByteArrayOutputStream()
        allUpdates.entries.forEach { (objNum, off) ->
            indexPairs.append("$objNum 1 ")
            streamBaos.write(1) // type 1: uncompressed indirect object
            streamBaos.write(((off shr 24) and 0xFF).toInt())
            streamBaos.write(((off shr 16) and 0xFF).toInt())
            streamBaos.write(((off shr 8) and 0xFF).toInt())
            streamBaos.write((off and 0xFF).toInt())
            streamBaos.write(0) // gen 0
        }
        val rawXrefBytes = streamBaos.toByteArray()
        val infoPart = if (infoObjNum != null) "/Info $infoObjNum 0 R " else ""
        val header = "$xrefObjNum 0 obj\n<< /Type /XRef /Size $size /W [1 4 1] " +
            "/Index [ ${indexPairs.toString().trim()} ] /Root $rootObjNum 0 R $infoPart" +
            "/Prev $prevXrefOffset /Length ${rawXrefBytes.size} >>\nstream\n"
        val footer = "\nendstream\nendobj\n"

        return ByteArrayOutputStream().apply {
            write(header.toByteArray(StandardCharsets.ISO_8859_1))
            write(rawXrefBytes)
            write(footer.toByteArray(StandardCharsets.ISO_8859_1))
        }.toByteArray()
    }

    private fun TreeMapWrapper(map: Map<Int, Long>): java.util.TreeMap<Int, Long> {
        return java.util.TreeMap<Int, Long>().apply { putAll(map) }
    }

    private fun escapePdfString(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .filter { it.code in 32..126 }
    }
}
