package com.example.core.pdf

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

data class PdfXrefEntry(
    val objNum: Int,
    val byteOffset: Long,
    val gen: Int,
    val inObjectStreamNum: Int? = null,
    val indexInObjectStream: Int = -1
)

data class PdfPageRefInfo(
    val pageIndex: Int,
    val pageObjNum: Int,
    val pageDictText: String,
    val widthPt: Float,
    val heightPt: Float,
    val mediaBoxOriginX: Float,
    val mediaBoxOriginY: Float,
    val contentObjNums: List<Int>,
    val resourcesRefOrInline: String
)

data class PdfParsedStructure(
    val pdfVersion: String,
    val isXRefStreamBased: Boolean,
    val lastXrefOffset: Long,
    val maxObjNum: Int,
    val rootObjNum: Int,
    val infoObjNum: Int?,
    val xrefEntries: Map<Int, PdfXrefEntry>,
    val objectStreamCache: Map<Int, Map<Int, String>>,
    val pages: List<PdfPageRefInfo>,
    val title: String,
    val author: String,
    val subject: String,
    val creator: String
)

/**
 * Pure-Kotlin PDF 1.0-2.0 object & cross-reference parser supporting both classic xref tables
 * and PDF 1.5+ /Type /XRef + /ObjStm compressed object streams.
 */
object PdfObjectStructure {

    fun parse(file: File): PdfParsedStructure? {
        if (!file.exists() || file.length() < 16) return null
        val bytes = file.readBytes()
        return parse(bytes)
    }

    fun parse(bytes: ByteArray): PdfParsedStructure? {
        val header = String(bytes, 0, minOf(bytes.size, 32), StandardCharsets.ISO_8859_1)
        val versionMatch = Regex("%PDF-(\\d+\\.\\d+)").find(header)
        val pdfVersion = versionMatch?.groupValues?.get(1) ?: "1.7"

        val startXrefOffset = findLastStartXref(bytes) ?: return null
        val xrefEntries = linkedMapOf<Int, PdfXrefEntry>()
        var rootObjNum = -1
        var infoObjNum: Int? = null
        var maxObjNum = 0
        var isXRefStream = false

        val visitedOffsets = mutableSetOf<Long>()
        var cursorOffset: Long? = startXrefOffset
        var firstPass = true

        while (cursorOffset != null && cursorOffset >= 0 && cursorOffset < bytes.size) {
            if (!visitedOffsets.add(cursorOffset)) break
            val sliceStart = cursorOffset.toInt()
            val snippet = String(
                bytes,
                sliceStart,
                minOf(bytes.size - sliceStart, 256),
                StandardCharsets.ISO_8859_1
            ).trimStart()

            if (snippet.startsWith("xref")) {
                val parsed = parseClassicXrefSection(bytes, sliceStart, xrefEntries)
                if (firstPass) {
                    isXRefStream = false
                }
                if (rootObjNum == -1) rootObjNum = parsed.rootObjNum
                if (infoObjNum == null) infoObjNum = parsed.infoObjNum
                maxObjNum = maxOf(maxObjNum, parsed.size - 1)
                cursorOffset = parsed.prevOffset
            } else {
                // PDF 1.5+ /Type /XRef stream
                if (firstPass) {
                    isXRefStream = true
                }
                val parsed = parseXrefStreamSection(bytes, sliceStart, xrefEntries) ?: break
                if (rootObjNum == -1) rootObjNum = parsed.rootObjNum
                if (infoObjNum == null) infoObjNum = parsed.infoObjNum
                maxObjNum = maxOf(maxObjNum, parsed.size - 1)
                cursorOffset = parsed.prevOffset
            }
            firstPass = false
        }

        if (rootObjNum <= 0) return null
        maxObjNum = maxOf(maxObjNum, xrefEntries.keys.maxOrNull() ?: 0)

        val objStreamCache = mutableMapOf<Int, Map<Int, String>>()

        fun getObjectBody(objNum: Int): String? {
            val entry = xrefEntries[objNum] ?: return null
            return if (entry.inObjectStreamNum != null) {
                val streamMap = objStreamCache.getOrPut(entry.inObjectStreamNum) {
                    unpackObjectStream(bytes, xrefEntries[entry.inObjectStreamNum])
                }
                streamMap[objNum]
            } else {
                readIndirectObjectText(bytes, entry.byteOffset.toInt())
            }
        }

        val rootText = getObjectBody(rootObjNum) ?: return null
        val pagesRootNum = extractRefNumber(rootText, "/Pages") ?: return null
        val pages = collectPages(pagesRootNum, ::getObjectBody)

        val infoText = infoObjNum?.let { getObjectBody(it) } ?: ""
        val title = extractPdfString(infoText, "/Title")
        val author = extractPdfString(infoText, "/Author")
        val subject = extractPdfString(infoText, "/Subject")
        val creator = extractPdfString(infoText, "/Creator")

        return PdfParsedStructure(
            pdfVersion = pdfVersion,
            isXRefStreamBased = isXRefStream,
            lastXrefOffset = startXrefOffset,
            maxObjNum = maxObjNum,
            rootObjNum = rootObjNum,
            infoObjNum = infoObjNum,
            xrefEntries = xrefEntries,
            objectStreamCache = objStreamCache,
            pages = pages,
            title = title,
            author = author,
            subject = subject,
            creator = creator
        )
    }

    fun getObjectStreamBytes(bytes: ByteArray, structure: PdfParsedStructure, objNum: Int): ByteArray? {
        val entry = structure.xrefEntries[objNum] ?: return null
        if (entry.inObjectStreamNum != null) return null
        val start = entry.byteOffset.toInt()
        if (start < 0 || start >= bytes.size) return null
        val rawWindow = String(
            bytes,
            start,
            minOf(bytes.size - start, 65536),
            StandardCharsets.ISO_8859_1
        )
        val dictStart = rawWindow.indexOf("<<")
        val streamKw = indexOfKeyword(bytes, start, "stream")
        if (dictStart < 0 || streamKw < 0) return null
        val dictEnd = findMatchingDictEnd(rawWindow, dictStart)
        val dict = if (dictEnd > dictStart) rawWindow.substring(dictStart, dictEnd + 2) else ""

        var dataStart = streamKw + 6
        if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
        if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

        val endStreamIdx = indexOfKeyword(bytes, dataStart, "endstream")
        if (endStreamIdx < dataStart) return null
        var dataEnd = endStreamIdx
        if (dataEnd > dataStart && bytes[dataEnd - 1] == '\n'.code.toByte()) dataEnd--
        if (dataEnd > dataStart && bytes[dataEnd - 1] == '\r'.code.toByte()) dataEnd--

        val rawStream = bytes.copyOfRange(dataStart, dataEnd)
        return if (dict.contains("/FlateDecode")) {
            inflateBytes(rawStream) ?: rawStream
        } else {
            rawStream
        }
    }

    private fun collectPages(
        nodeObjNum: Int,
        getObjectBody: (Int) -> String?,
        inheritedMediaBox: FloatArray = floatArrayOf(0f, 0f, 612f, 792f),
        inheritedResources: String = "<< >>"
    ): List<PdfPageRefInfo> {
        val out = mutableListOf<PdfPageRefInfo>()
        val body = getObjectBody(nodeObjNum) ?: return out
        val mediaBox = extractMediaBox(body) ?: inheritedMediaBox
        val resources = extractDictOrRef(body, "/Resources") ?: inheritedResources

        if (body.contains("/Type /Pages") || body.contains("/Type/Pages") || body.contains("/Kids")) {
            val kidsMatch = Regex("/Kids\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(body)
            if (kidsMatch != null) {
                val refs = Regex("(\\d+)\\s+\\d+\\s+R").findAll(kidsMatch.groupValues[1])
                refs.forEach { m ->
                    val childNum = m.groupValues[1].toIntOrNull()
                    if (childNum != null) {
                        out.addAll(collectPages(childNum, getObjectBody, mediaBox, resources))
                    }
                }
                if (out.isNotEmpty()) return out
            }
        }

        // Leaf /Page node
        val pageIndex = out.size
        val contentRefs = extractContentObjNums(body)
        val w = (mediaBox[2] - mediaBox[0]).let { if (it > 0f) it else 612f }
        val h = (mediaBox[3] - mediaBox[1]).let { if (it > 0f) it else 792f }
        out.add(
            PdfPageRefInfo(
                pageIndex = pageIndex,
                pageObjNum = nodeObjNum,
                pageDictText = body,
                widthPt = w,
                heightPt = h,
                mediaBoxOriginX = mediaBox[0],
                mediaBoxOriginY = mediaBox[1],
                contentObjNums = contentRefs,
                resourcesRefOrInline = resources
            )
        )
        return out.mapIndexed { idx, info -> info.copy(pageIndex = idx) }
    }

    private fun extractContentObjNums(pageDict: String): List<Int> {
        val arrayMatch = Regex("/Contents\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(pageDict)
        if (arrayMatch != null) {
            return Regex("(\\d+)\\s+\\d+\\s+R")
                .findAll(arrayMatch.groupValues[1])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
        }
        val singleMatch = Regex("/Contents\\s+(\\d+)\\s+\\d+\\s+R").find(pageDict)
        return singleMatch?.groupValues?.get(1)?.toIntOrNull()?.let { listOf(it) } ?: emptyList()
    }

    private fun extractMediaBox(dict: String): FloatArray? {
        val m = Regex("/(?:CropBox|MediaBox)\\s*\\[\\s*([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s*\\]")
            .find(dict) ?: return null
        return floatArrayOf(
            m.groupValues[1].toFloatOrNull() ?: 0f,
            m.groupValues[2].toFloatOrNull() ?: 0f,
            m.groupValues[3].toFloatOrNull() ?: 612f,
            m.groupValues[4].toFloatOrNull() ?: 792f
        )
    }

    private fun extractDictOrRef(dict: String, key: String): String? {
        val refMatch = Regex("$key\\s+(\\d+\\s+\\d+\\s+R)").find(dict)
        if (refMatch != null) return refMatch.groupValues[1]
        val keyIdx = dict.indexOf(key)
        if (keyIdx < 0) return null
        val sub = dict.substring(keyIdx + key.length).trimStart()
        if (!sub.startsWith("<<")) return null
        val end = findMatchingDictEnd(sub, 0)
        return if (end > 0) sub.substring(0, end + 2) else null
    }

    private fun findMatchingDictEnd(text: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < text.length - 1) {
            if (text[i] == '<' && text[i + 1] == '<') {
                depth++
                i += 2
                continue
            } else if (text[i] == '>' && text[i + 1] == '>') {
                depth--
                if (depth == 0) return i
                i += 2
                continue
            }
            i++
        }
        return -1
    }

    private fun findLastStartXref(bytes: ByteArray): Long? {
        val tailStart = maxOf(0, bytes.size - 2048)
        val tail = String(bytes, tailStart, bytes.size - tailStart, StandardCharsets.ISO_8859_1)
        val match = Regex("startxref\\s+(\\d+)").findAll(tail).lastOrNull() ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private data class ParsedTrailerInfo(
        val size: Int,
        val rootObjNum: Int,
        val infoObjNum: Int?,
        val prevOffset: Long?
    )

    private fun parseClassicXrefSection(
        bytes: ByteArray,
        offset: Int,
        outEntries: MutableMap<Int, PdfXrefEntry>
    ): ParsedTrailerInfo {
        val endIdx = indexOfKeyword(bytes, offset, "trailer").let {
            if (it >= 0) minOf(bytes.size, it + 4096) else minOf(bytes.size, offset + 65536)
        }
        val section = String(bytes, offset, endIdx - offset, StandardCharsets.ISO_8859_1)
        val trailerIdx = section.indexOf("trailer")
        val xrefText = if (trailerIdx > 0) section.substring(0, trailerIdx) else section
        val trailerText = if (trailerIdx >= 0) section.substring(trailerIdx) else ""

        val lines = xrefText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var i = 0
        if (i < lines.size && lines[i].startsWith("xref")) i++
        while (i < lines.size) {
            val parts = lines[i].split(Regex("\\s+"))
            if (parts.size == 2 && parts[0].all { it.isDigit() } && parts[1].all { it.isDigit() }) {
                val startNum = parts[0].toInt()
                val count = parts[1].toInt()
                i++
                for (k in 0 until count) {
                    if (i >= lines.size) break
                    val entryParts = lines[i].split(Regex("\\s+"))
                    if (entryParts.size >= 3) {
                        val objOffset = entryParts[0].toLongOrNull() ?: 0L
                        val gen = entryParts[1].toIntOrNull() ?: 0
                        val flag = entryParts[2]
                        val objNum = startNum + k
                        if (flag == "n" && !outEntries.containsKey(objNum)) {
                            outEntries[objNum] = PdfXrefEntry(objNum, objOffset, gen)
                        }
                    }
                    i++
                }
            } else {
                i++
            }
        }

        val size = Regex("/Size\\s+(\\d+)").find(trailerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val root = extractRefNumber(trailerText, "/Root") ?: -1
        val info = extractRefNumber(trailerText, "/Info")
        val prev = Regex("/Prev\\s+(\\d+)").find(trailerText)?.groupValues?.get(1)?.toLongOrNull()
        return ParsedTrailerInfo(size, root, info, prev)
    }

    private fun parseXrefStreamSection(
        bytes: ByteArray,
        offset: Int,
        outEntries: MutableMap<Int, PdfXrefEntry>
    ): ParsedTrailerInfo? {
        val window = String(
            bytes,
            offset,
            minOf(bytes.size - offset, 4096),
            StandardCharsets.ISO_8859_1
        )
        val dictStart = window.indexOf("<<")
        if (dictStart < 0) return null
        val dictEnd = findMatchingDictEnd(window, dictStart)
        if (dictEnd < 0) return null
        val dict = window.substring(dictStart, dictEnd + 2)
        if (!dict.contains("/XRef")) return null

        val size = Regex("/Size\\s+(\\d+)").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val root = extractRefNumber(dict, "/Root") ?: -1
        val info = extractRefNumber(dict, "/Info")
        val prev = Regex("/Prev\\s+(\\d+)").find(dict)?.groupValues?.get(1)?.toLongOrNull()
        val wMatch = Regex("/W\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*\\]").find(dict) ?: return null
        val w0 = wMatch.groupValues[1].toInt()
        val w1 = wMatch.groupValues[2].toInt()
        val w2 = wMatch.groupValues[3].toInt()
        val stride = w0 + w1 + w2

        val indexNums = Regex("/Index\\s*\\[([\\d\\s]+)\\]").find(dict)?.groupValues?.get(1)
            ?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }
            ?: listOf(0, size)

        val streamKw = indexOfKeyword(bytes, offset + dictEnd, "stream")
        if (streamKw < 0) return null
        var dataStart = streamKw + 6
        if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
        if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++
        val lengthAttr = Regex("/Length\\s+(\\d+)").find(dict)?.groupValues?.get(1)?.toIntOrNull()
        val endStreamIdx = if (lengthAttr != null && dataStart + lengthAttr <= bytes.size) {
            dataStart + lengthAttr
        } else {
            indexOfKeyword(bytes, dataStart, "endstream")
        }
        if (endStreamIdx < dataStart) return null
        val raw = bytes.copyOfRange(dataStart, endStreamIdx)
        val decoded = if (dict.contains("/FlateDecode")) (inflateBytes(raw) ?: return null) else raw

        var pos = 0
        var pairIdx = 0
        while (pairIdx + 1 < indexNums.size) {
            val startObj = indexNums[pairIdx]
            val count = indexNums[pairIdx + 1]
            for (k in 0 until count) {
                if (pos + stride > decoded.size) break
                val type = if (w0 == 0) 1L else readBigEndian(decoded, pos, w0)
                val f1 = readBigEndian(decoded, pos + w0, w1)
                val f2 = readBigEndian(decoded, pos + w0 + w1, w2)
                pos += stride
                val objNum = startObj + k
                if (!outEntries.containsKey(objNum)) {
                    when (type) {
                        1L -> outEntries[objNum] = PdfXrefEntry(objNum, f1, f2.toInt())
                        2L -> outEntries[objNum] = PdfXrefEntry(
                            objNum = objNum,
                            byteOffset = 0L,
                            gen = 0,
                            inObjectStreamNum = f1.toInt(),
                            indexInObjectStream = f2.toInt()
                        )
                    }
                }
            }
            pairIdx += 2
        }
        return ParsedTrailerInfo(size, root, info, prev)
    }

    private fun unpackObjectStream(bytes: ByteArray, entry: PdfXrefEntry?): Map<Int, String> {
        if (entry == null) return emptyMap()
        val offset = entry.byteOffset.toInt()
        if (offset < 0 || offset >= bytes.size) return emptyMap()
        val header = String(bytes, offset, minOf(bytes.size - offset, 2048), StandardCharsets.ISO_8859_1)
        val dictStart = header.indexOf("<<")
        val dictEnd = if (dictStart >= 0) findMatchingDictEnd(header, dictStart) else -1
        if (dictEnd < 0) return emptyMap()
        val dict = header.substring(dictStart, dictEnd + 2)
        val n = Regex("/N\\s+(\\d+)").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: return emptyMap()
        val first = Regex("/First\\s+(\\d+)").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: return emptyMap()

        val streamKw = indexOfKeyword(bytes, offset + dictEnd, "stream")
        if (streamKw < 0) return emptyMap()
        var dataStart = streamKw + 6
        if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
        if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++
        val length = Regex("/Length\\s+(\\d+)").find(dict)?.groupValues?.get(1)?.toIntOrNull()
        val dataEnd = if (length != null && dataStart + length <= bytes.size) {
            dataStart + length
        } else {
            indexOfKeyword(bytes, dataStart, "endstream")
        }
        if (dataEnd < dataStart) return emptyMap()
        val raw = bytes.copyOfRange(dataStart, dataEnd)
        val decoded = if (dict.contains("/FlateDecode")) (inflateBytes(raw) ?: return emptyMap()) else raw
        if (first > decoded.size) return emptyMap()

        val indexHeader = String(decoded, 0, first, StandardCharsets.ISO_8859_1)
        val nums = indexHeader.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
        val result = mutableMapOf<Int, String>()
        for (i in 0 until n) {
            val pairStart = i * 2
            if (pairStart + 1 >= nums.size) break
            val objNum = nums[pairStart]
            val relOffset = first + nums[pairStart + 1]
            val nextOffset = if (i + 1 < n && (i + 1) * 2 + 1 < nums.size) {
                first + nums[(i + 1) * 2 + 1]
            } else {
                decoded.size
            }
            if (relOffset in 0..nextOffset && nextOffset <= decoded.size) {
                result[objNum] = String(
                    decoded,
                    relOffset,
                    nextOffset - relOffset,
                    StandardCharsets.ISO_8859_1
                )
            }
        }
        return result
    }

    private fun readIndirectObjectText(bytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset >= bytes.size) return null
        val end = indexOfKeyword(bytes, offset, "endobj").let {
            if (it >= 0) it else minOf(bytes.size, offset + 16384)
        }
        return String(bytes, offset, end - offset, StandardCharsets.ISO_8859_1)
    }

    private fun extractRefNumber(text: String, key: String): Int? {
        return Regex("$key\\s+(\\d+)\\s+\\d+\\s+R").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractPdfString(text: String, key: String): String {
        val m = Regex("$key\\s*\\(([^)]*)\\)").find(text) ?: return ""
        return m.groupValues[1]
    }

    private fun readBigEndian(bytes: ByteArray, start: Int, len: Int): Long {
        var v = 0L
        for (i in 0 until len) {
            v = (v shl 8) or (bytes[start + i].toLong() and 0xFFL)
        }
        return v
    }

    private fun indexOfKeyword(bytes: ByteArray, from: Int, keyword: String): Int {
        val kw = keyword.toByteArray(StandardCharsets.ISO_8859_1)
        val limit = bytes.size - kw.size
        var i = maxOf(0, from)
        while (i <= limit) {
            var matched = true
            for (j in kw.indices) {
                if (bytes[i + j] != kw[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
            i++
        }
        return -1
    }

    fun inflateBytes(compressed: ByteArray): ByteArray? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val baos = ByteArrayOutputStream(compressed.size * 2)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    baos.write(buffer, 0, count)
                }
            }
            inflater.end()
            baos.toByteArray()
        }.getOrNull()
    }
}
