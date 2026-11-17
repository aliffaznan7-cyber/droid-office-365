package com.example.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PdfPageDimension(
    val pageIndex: Int,
    val widthPt: Int,
    val heightPt: Int
) {
    val aspectRatio: Float
        get() = if (heightPt > 0) widthPt.toFloat() / heightPt.toFloat() else 0.7727f
}

/**
 * Hardware-friendly 144Hz-ready PDF rendering pipeline backed by Android PdfRenderer (PDFium)
 * with Mutex-guarded page access, dynamic LruCache, and zoom-tier bitmap rendering.
 */
class PdfRenderEngine {

    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 8L)
        .toInt()
        .coerceIn(24 * 1024, 96 * 1024)

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val mutex = Mutex()
    private var openPath: String? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var cachedDimensions: List<PdfPageDimension> = emptyList()

    suspend fun openDocument(file: File): List<PdfPageDimension> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (openPath == file.absolutePath && pdfRenderer != null && cachedDimensions.isNotEmpty()) {
                return@withLock cachedDimensions
            }
            closeInternal()
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val dims = (0 until renderer.pageCount).map { idx ->
                renderer.openPage(idx).use { page ->
                    PdfPageDimension(
                        pageIndex = idx,
                        widthPt = page.width.coerceAtLeast(1),
                        heightPt = page.height.coerceAtLeast(1)
                    )
                }
            }
            openPath = file.absolutePath
            fileDescriptor = pfd
            pdfRenderer = renderer
            cachedDimensions = dims
            dims
        }
    }

    suspend fun invalidateDocumentCache(documentKey: String) {
        mutex.withLock {
            val snapshot = bitmapCache.snapshot()
            snapshot.keys.filter { it.startsWith("${documentKey}_") }.forEach {
                bitmapCache.remove(it)
            }
            // Reopen underlying renderer on next request if file changed on disk
            closeInternal()
        }
    }

    fun getCachedBitmap(documentKey: String, pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val bucketWidth = bucketizeWidth(targetWidthPx)
        return bitmapCache.get("${documentKey}_${pageIndex}_${bucketWidth}")
    }

    suspend fun renderPage(
        file: File,
        documentKey: String,
        pageIndex: Int,
        targetWidthPx: Int,
        isThumbnail: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val bucketWidth = if (isThumbnail) {
            targetWidthPx.coerceIn(140, 280)
        } else {
            bucketizeWidth(targetWidthPx).coerceIn(320, 2600)
        }
        val cacheKey = "${documentKey}_${pageIndex}_${bucketWidth}"
        bitmapCache.get(cacheKey)?.let { return@withContext it }

        mutex.withLock {
            bitmapCache.get(cacheKey)?.let { return@withContext it }
            ensureOpenLocked(file)
            val renderer = pdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            return@withLock renderer.openPage(pageIndex).use { page ->
                val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                val bmpWidth = bucketWidth
                val bmpHeight = (bmpWidth * aspect).roundToInt().coerceAtLeast(1)
                val config = if (isThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, config)
                bitmap.eraseColor(Color.WHITE)

                val matrix = Matrix().apply {
                    val scaleX = bmpWidth.toFloat() / page.width.toFloat()
                    val scaleY = bmpHeight.toFloat() / page.height.toFloat()
                    setScale(scaleX, scaleY)
                }
                page.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
        }
    }

    suspend fun prefetchAdjacentPages(
        file: File,
        documentKey: String,
        currentPage: Int,
        totalPages: Int,
        targetWidthPx: Int
    ) {
        val indices = listOf(currentPage + 1, currentPage + 2, currentPage - 1)
            .filter { it in 0 until totalPages }
        for (idx in indices) {
            val bucket = bucketizeWidth(targetWidthPx).coerceIn(320, 2600)
            if (bitmapCache.get("${documentKey}_${idx}_${bucket}") == null) {
                runCatching { renderPage(file, documentKey, idx, targetWidthPx, isThumbnail = false) }
            }
        }
    }

    private fun ensureOpenLocked(file: File) {
        if (openPath == file.absolutePath && pdfRenderer != null) return
        closeInternal()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        openPath = file.absolutePath
        fileDescriptor = pfd
        pdfRenderer = renderer
    }

    private fun bucketizeWidth(widthPx: Int): Int {
        // Snap to 128px buckets so 144Hz pinch-zoom doesn't re-render on every single pixel delta
        return ((widthPx + 63) / 128) * 128
    }

    suspend fun close() {
        mutex.withLock {
            closeInternal()
        }
    }

    private fun closeInternal() {
        runCatching { pdfRenderer?.close() }
        runCatching { fileDescriptor?.close() }
        pdfRenderer = null
        fileDescriptor = null
        openPath = null
        cachedDimensions = emptyList()
    }
}
