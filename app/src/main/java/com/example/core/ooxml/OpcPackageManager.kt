package com.example.core.ooxml

import android.util.Xml
import com.example.domain.model.CoreDocumentProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Open Packaging Conventions (OPC) reader & writer per ECMA-376 / ISO/IEC 29500 (Part 1 Sec 4.2).
 */
object OpcPackageManager {

    suspend fun readPackageParts(file: File): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(FileInputStream(file).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    parts[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        parts
    }

    suspend fun streamSinglePart(
        file: File,
        entryName: String,
        consumer: (InputStream) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        ZipInputStream(FileInputStream(file).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryName) {
                    consumer(zis)
                    return@withContext true
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        false
    }

    suspend fun writePackageParts(file: File, parts: Map<String, ByteArray>) =
        withContext(Dispatchers.IO) {
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            ZipOutputStream(FileOutputStream(tempFile).buffered()).use { zos ->
                parts.forEach { (name, bytes) ->
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        }

    fun parseCoreProperties(xmlBytes: ByteArray?): CoreDocumentProperties {
        if (xmlBytes == null) return CoreDocumentProperties()
        var title = ""
        var creator = ""
        var subject = ""
        var description = ""
        var created = ""
        var modified = ""

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8.name())
            var event = parser.eventType
            var currentTag = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> currentTag = parser.name ?: ""
                    XmlPullParser.TEXT -> {
                        val value = parser.text ?: ""
                        when (currentTag) {
                            "title" -> title += value
                            "creator" -> creator += value
                            "subject" -> subject += value
                            "description" -> description += value
                            "created" -> created += value
                            "modified" -> modified += value
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                event = parser.next()
            }
        }

        return CoreDocumentProperties(
            title = title.trim(),
            creator = creator.trim().ifEmpty { "DocuDesk User" },
            subject = subject.trim(),
            description = description.trim(),
            created = created.trim(),
            modified = modified.trim()
        )
    }

    fun buildCorePropertiesXml(props: CoreDocumentProperties): ByteArray {
        val nowIso = isoTimestampNow()
        val created = props.created.ifBlank { nowIso }
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append(
                """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" """ +
                    """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
                    """xmlns:dcterms="http://purl.org/dc/terms/" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">"""
            )
            append("<dc:title>${escapeXml(props.title)}</dc:title>")
            append("<dc:subject>${escapeXml(props.subject)}</dc:subject>")
            append("<dc:creator>${escapeXml(props.creator.ifBlank { "DocuDesk User" })}</dc:creator>")
            append("<dc:description>${escapeXml(props.description)}</dc:description>")
            append("""<dcterms:created xsi:type="dcterms:W3CDTF">${escapeXml(created)}</dcterms:created>""")
            append("""<dcterms:modified xsi:type="dcterms:W3CDTF">$nowIso</dcterms:modified>""")
            append("</cp:coreProperties>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isoTimestampNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
