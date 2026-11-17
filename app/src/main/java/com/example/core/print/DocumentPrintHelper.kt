package com.example.core.print

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxOrientation
import com.example.domain.model.DocxParagraphStyle
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Native Android PrintManager integration (Ctrl+P) for PDFs and WordprocessingML DOCX documents.
 */
object DocumentPrintHelper {

    fun printPdfFile(context: Context, jobName: String, file: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing destination")
                    return
                }
                runCatching {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure {
                    callback?.onWriteFailed(it.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    fun printDocxModel(context: Context, model: DocxDocumentModel) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = model.coreProperties.title.ifBlank { "DocuDesk_Document" }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(
                if (model.orientation == DocxOrientation.LANDSCAPE) {
                    PrintAttributes.MediaSize.ISO_A4.asLandscape()
                } else {
                    PrintAttributes.MediaSize.ISO_A4
                }
            )
            .build()

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing output descriptor")
                    return
                }
                runCatching {
                    val pdf = PdfDocument()
                    val pageW = (model.paperSize.widthIn * 72f).toInt().let {
                        if (model.orientation == DocxOrientation.LANDSCAPE) (model.paperSize.heightIn * 72f).toInt() else it
                    }
                    val pageH = (model.paperSize.heightIn * 72f).toInt().let {
                        if (model.orientation == DocxOrientation.LANDSCAPE) (model.paperSize.widthIn * 72f).toInt() else it
                    }
                    var pageNum = 1
                    var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    var canvas = page.canvas
                    val leftX = model.margins.leftInches * 72f
                    val maxX = pageW - model.margins.rightInches * 72f
                    var y = model.margins.topInches * 72f + 16f

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 11f
                    }

                    fun ensureSpace(needed: Float) {
                        if (y + needed > pageH - model.margins.bottomInches * 72f) {
                            pdf.finishPage(page)
                            pageNum++
                            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                            canvas = page.canvas
                            y = model.margins.topInches * 72f + 16f
                        }
                    }

                    model.blocks.forEach { block ->
                        when (block) {
                            is DocxBlock.PageBreak -> {
                                pdf.finishPage(page)
                                pageNum++
                                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                                canvas = page.canvas
                                y = model.margins.topInches * 72f + 16f
                            }
                            is DocxBlock.Paragraph -> {
                                val text = block.runs.joinToString("") { it.text }
                                val isHeading = block.style.name.startsWith("HEADING_") || block.style == DocxParagraphStyle.TITLE
                                paint.textSize = if (isHeading) 15f else 11f
                                paint.typeface = if (isHeading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                                val words = text.split(" ")
                                val line = StringBuilder()
                                val avail = (maxX - leftX).coerceAtLeast(100f)
                                words.forEach { w ->
                                    val candidate = if (line.isEmpty()) w else "$line $w"
                                    if (paint.measureText(candidate) > avail) {
                                        ensureSpace(16f)
                                        canvas.drawText(line.toString(), leftX, y, paint)
                                        y += 15f
                                        line.clear()
                                        line.append(w)
                                    } else {
                                        line.clear()
                                        line.append(candidate)
                                    }
                                }
                                if (line.isNotEmpty()) {
                                    ensureSpace(16f)
                                    canvas.drawText(line.toString(), leftX, y, paint)
                                    y += 18f
                                }
                            }
                            is DocxBlock.Table -> {
                                block.rows.forEach { row ->
                                    ensureSpace(18f)
                                    canvas.drawText(row.joinToString(" | "), leftX, y, paint)
                                    y += 16f
                                }
                                y += 8f
                            }
                            else -> {
                                ensureSpace(18f)
                                y += 14f
                            }
                        }
                    }
                    pdf.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { out -> pdf.writeTo(out) }
                    pdf.close()
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure {
                    callback?.onWriteFailed(it.message)
                }
            }
        }
        printManager.print(jobName, adapter, attrs)
    }
}
