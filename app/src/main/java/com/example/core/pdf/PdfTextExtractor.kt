package com.example.core.pdf

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.domain.model.NormalizedRect
import com.example.domain.model.PdfSearchHit
import com.example.domain.model.PdfTextRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Cross-API PDF text extraction, coordinate mapping, and full-document search engine.
 * Leverages Android 15+ (API 35+) native PdfRenderer.Page text/search APIs when present,
 * and falls back to pure-Kotlin PDF content-stream BT/ET parser on API 24-34.
 */
object PdfTextExtractor {

    suspend fun extractPageTextRuns(
        file: File,
        pageIndex: Int
    ): List<PdfTextRun> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val nativeRuns = runCatching { extractViaApi35(file, pageIndex) }.getOrNull()
            if (!nativeRuns.isNullOrEmpty()) return@withContext nativeRuns
        }
        extractViaContentStream(file, pageIndex)
    }

    suspend fun searchDocument(
        file: File,
        query: String,
        maxHits: Int = 80
    ): List<PdfSearchHit> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val hits = mutableListOf<PdfSearchHit>()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext emptyList()
        val structure = PdfObjectStructure.parse(bytes) ?: return@withContext emptyList()

        for (page in structure.pages) {
            if (hits.size >= maxHits) break
            val runs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { extractViaApi35(file, page.pageIndex) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: extractPageRunsFromBytes(bytes, structure, page)
            } else {
                extractPageRunsFromBytes(bytes, structure, page)
            }

            runs.forEach { run ->
                if (hits.size < maxHits && run.text.contains(trimmed, ignoreCase = true)) {
                    hits.add(
                        PdfSearchHit(
                            pageIndex = page.pageIndex,
                            snippet = run.text.take(96),
                            bounds = run.bounds
                        )
                    )
                }
            }
        }
        hits
    }

    private fun extractViaApi35(file: File, pageIndex: Int): List<PdfTextRun> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return emptyList()
        val runs = mutableListOf<PdfTextRun>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return emptyList()
                renderer.openPage(pageIndex).use { page ->
                    val w = page.width.coerceAtLeast(1).toFloat()
                    val h = page.height.coerceAtLeast(1).toFloat()
                    val contents = page.textContents
                    contents.forEach { content ->
                        val text = content.text.trim()
                        if (text.isNotEmpty()) {
                            // PdfPageTextContent provides text; compute line-level normalized bounds if needed
                            val bounds = NormalizedRect(0.06f, 0.1f, 0.94f, 0.9f)
                            runs.add(PdfTextRun(text = text, pageIndex = pageIndex, bounds = bounds))
                        }
                    }
                    // If search/selection bounds are desired per line, merge with content stream bounds when available
                    if (runs.size == 1 && runs.first().text.length > 180) {
                        // Split long single-block text into searchable lines
                        val lines = runs.first().text.lines().filter { it.isNotBlank() }
                        runs.clear()
                        val total = lines.size.coerceAtLeast(1)
                        lines.forEachIndexed { idx, line ->
                            val top = 0.06f + (idx.toFloat() / total) * 0.86f
                            val bottom = (top + (0.82f / total)).coerceAtMost(0.95f)
                            runs.add(
                                PdfTextRun(
                                    text = line.trim(),
                                    pageIndex = pageIndex,
                                    bounds = NormalizedRect(0.08f, top, 0.92f, bottom)
                                )
                            )
                        }
                    }
                }
            }
        }
        return runs
    }

    private fun extractViaContentStream(file: File, pageIndex: Int): List<PdfTextRun> {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()
        val structure = PdfObjectStructure.parse(bytes) ?: return emptyList()
        val page = structure.pages.getOrNull(pageIndex) ?: return emptyList()
        return extractPageRunsFromBytes(bytes, structure, page)
    }

    private fun extractPageRunsFromBytes(
        bytes: ByteArray,
        structure: PdfParsedStructure,
        page: PdfPageRefInfo
    ): List<PdfTextRun> {
        val runs = mutableListOf<PdfTextRun>()
        val combinedStream = StringBuilder()
        page.contentObjNums.forEach { objNum ->
            val streamBytes = PdfObjectStructure.getObjectStreamBytes(bytes, structure, objNum)
            if (streamBytes != null) {
                combinedStream.append(String(streamBytes, StandardCharsets.ISO_8859_1)).append('\n')
            }
        }
        val content = combinedStream.toString()
        if (content.isBlank()) return emptyList()

        val btBlocks = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL).findAll(content)
        val pw = page.widthPt.coerceAtLeast(1f)
        val ph = page.heightPt.coerceAtLeast(1f)

        btBlocks.forEach { blockMatch ->
            val block = blockMatch.groupValues[1]
            var curX = 72f
            var curY = ph - 72f
            var fontSize = 11f

            Regex("/[A-Za-z0-9_+]+\\s+([\\d.]+)\\s+Tf").find(block)?.let {
                fontSize = it.groupValues[1].toFloatOrNull()?.coerceIn(6f, 48f) ?: 11f
            }
            Regex("([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+Tm")
                .find(block)?.let {
                    curX = it.groupValues[5].toFloatOrNull() ?: curX
                    curY = it.groupValues[6].toFloatOrNull() ?: curY
                }

            val lineTokens = block.lines()
            lineTokens.forEach { rawLine ->
                val line = rawLine.trim()
                Regex("([\\d.-]+)\\s+([\\d.-]+)\\s+T[dD]").find(line)?.let { td ->
                    curX += td.groupValues[1].toFloatOrNull() ?: 0f
                    curY += td.groupValues[2].toFloatOrNull() ?: 0f
                }
                Regex("([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+Tm")
                    .find(line)?.let { tm ->
                        curX = tm.groupValues[5].toFloatOrNull() ?: curX
                        curY = tm.groupValues[6].toFloatOrNull() ?: curY
                    }

                val extracted = extractTextOperators(line)
                if (extracted.isNotBlank()) {
                    val estWidth = (extracted.length * fontSize * 0.48f).coerceAtLeast(18f)
                    val left = ((curX - page.mediaBoxOriginX) / pw).coerceIn(0.02f, 0.94f)
                    val right = ((curX - page.mediaBoxOriginX + estWidth) / pw).coerceIn(left + 0.02f, 0.98f)
                    val top = (1f - ((curY - page.mediaBoxOriginY + fontSize) / ph)).coerceIn(0.02f, 0.96f)
                    val bottom = (1f - ((curY - page.mediaBoxOriginY - fontSize * 0.25f) / ph))
                        .coerceIn(top + 0.012f, 0.99f)
                    runs.add(
                        PdfTextRun(
                            text = extracted,
                            pageIndex = page.pageIndex,
                            bounds = NormalizedRect(left, top, right, bottom)
                        )
                    )
                }
            }
        }
        return runs
    }

    private fun extractTextOperators(line: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            if (line[i] == '(') {
                i++
                while (i < line.length) {
                    val ch = line[i]
                    if (ch == '\\' && i + 1 < line.length) {
                        val next = line[i + 1]
                        when (next) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            '(', ')', '\\' -> sb.append(next)
                            else -> sb.append(next)
                        }
                        i += 2
                    } else if (ch == ')') {
                        i++
                        break
                    } else {
                        if (ch.code in 32..126) sb.append(ch)
                        i++
                    }
                }
            } else {
                i++
            }
        }
        return sb.toString().trim()
    }
}
