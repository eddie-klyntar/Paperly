package com.example.paperly.engines.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class PdfDocumentMetadata(
  val title: String?,
  val author: String?,
  val creator: String?,
  val producer: String?,
  val creationDate: String?,
  val pdfVersion: String?,
  val pageCount: Int,
  val isTextBased: Boolean
)

data class PdfOutlineNode(
  val title: String,
  val pageIndex: Int,
  val depth: Int
)

data class PdfSearchHit(
  val pageIndex: Int,
  val snippet: String,
  val matchCount: Int
)

class PdfSession(
  private val context: Context,
  private val uri: Uri,
  private val tempFile: File?,
  private val pfd: ParcelFileDescriptor,
  private val renderer: PdfRenderer
) {
  val pageCount: Int get() = renderer.pageCount
  private val renderMutex = Mutex()
  private val bitmapCache = object : LruCache<String, Bitmap>(8) {
    override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
      // Let GC reclaim naturally; Compose ImageBitmap may still reference until recomposed
    }
  }

  private var pdDocument: PDDocument? = null
  private val pdMutex = Mutex()

  suspend fun renderPage(pageIndex: Int, targetWidthPx: Int, rotationDegrees: Int = 0): Bitmap? =
    withContext(Dispatchers.IO) {
      if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null
      val safeWidth = targetWidthPx.coerceIn(120, 2200)
      val cacheKey = "${pageIndex}_${safeWidth}_$rotationDegrees"
      bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }

      renderMutex.withLock {
        bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }
        renderer.openPage(pageIndex).use { page ->
          val aspect = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
          val renderW = safeWidth
          val renderH = (safeWidth * aspect).toInt().coerceIn(120, 3400)
          val rawBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
          rawBitmap.eraseColor(Color.WHITE)
          page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

          val finalBitmap = if (rotationDegrees % 360 != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            if (rotated != rawBitmap) rawBitmap.recycle()
            rotated
          } else {
            rawBitmap
          }
          bitmapCache.put(cacheKey, finalBitmap)
          finalBitmap
        }
      }
    }

  private suspend fun ensurePdDocument(): PDDocument? = pdMutex.withLock {
    pdDocument?.let { return@withLock it }
    runCatching {
      val stream = tempFile?.inputStream() ?: context.contentResolver.openInputStream(uri)
      stream?.use { PDDocument.load(it) }?.also { pdDocument = it }
    }.getOrNull()
  }

  suspend fun readMetadataAndOutline(): Pair<PdfDocumentMetadata, List<PdfOutlineNode>> =
    withContext(Dispatchers.IO) {
      val doc = ensurePdDocument()
      if (doc == null) {
        return@withContext PdfDocumentMetadata(
          title = null,
          author = null,
          creator = null,
          producer = null,
          creationDate = null,
          pdfVersion = null,
          pageCount = pageCount,
          isTextBased = false
        ) to emptyList()
      }
      val info = doc.documentInformation
      val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
      val creationStr = runCatching { info?.creationDate?.time?.let { dateFmt.format(it) } }.getOrNull()

      // Sample first few pages to honestly determine whether embedded text layer exists
      val hasText = runCatching {
        val sampleLimit = minOf(doc.numberOfPages, 5)
        val stripper = PDFTextStripper().apply {
          startPage = 1
          endPage = sampleLimit
        }
        stripper.getText(doc).trim().length >= 12
      }.getOrDefault(false)

      val outlineNodes = mutableListOf<PdfOutlineNode>()
      runCatching {
        val root = doc.documentCatalog?.documentOutline
        fun walk(item: PDOutlineItem?, depth: Int) {
          var current = item
          var guard = 0
          while (current != null && guard < 300) {
            guard++
            val page = runCatching { current.findDestinationPage(doc) }.getOrNull()
            val idx = if (page != null) doc.pages.indexOf(page).coerceAtLeast(0) else 0
            val title = current.title?.trim().orEmpty()
            if (title.isNotEmpty()) {
              outlineNodes += PdfOutlineNode(title = title, pageIndex = idx, depth = depth)
            }
            if (depth < 3 && current.firstChild != null) {
              walk(current.firstChild, depth + 1)
            }
            current = current.nextSibling
          }
        }
        walk(root?.firstChild, 0)
      }

      val meta = PdfDocumentMetadata(
        title = info?.title?.takeIf { it.isNotBlank() },
        author = info?.author?.takeIf { it.isNotBlank() },
        creator = info?.creator?.takeIf { it.isNotBlank() },
        producer = info?.producer?.takeIf { it.isNotBlank() },
        creationDate = creationStr,
        pdfVersion = doc.version.takeIf { it > 0f }?.let { "PDF $it" },
        pageCount = pageCount,
        isTextBased = hasText
      )
      meta to outlineNodes
    }

  suspend fun extractPageText(pageIndex: Int): String = withContext(Dispatchers.IO) {
    val doc = ensurePdDocument() ?: return@withContext ""
    if (pageIndex < 0 || pageIndex >= doc.numberOfPages) return@withContext ""
    pdMutex.withLock {
      runCatching {
        PDFTextStripper().apply {
          startPage = pageIndex + 1
          endPage = pageIndex + 1
          sortByPosition = true
        }.getText(doc).trim()
      }.getOrDefault("")
    }
  }

  suspend fun search(query: String): List<PdfSearchHit> = withContext(Dispatchers.IO) {
    val clean = query.trim()
    if (clean.isEmpty()) return@withContext emptyList()
    val doc = ensurePdDocument() ?: return@withContext emptyList()
    val hits = mutableListOf<PdfSearchHit>()
    val total = doc.numberOfPages
    for (p in 0 until total) {
      ensureActive()
      val pageText = pdMutex.withLock {
        runCatching {
          PDFTextStripper().apply {
            startPage = p + 1
            endPage = p + 1
          }.getText(doc)
        }.getOrDefault("")
      }
      if (pageText.isBlank()) continue
      val lowerText = pageText.lowercase()
      val lowerQ = clean.lowercase()
      var idx = lowerText.indexOf(lowerQ)
      if (idx >= 0) {
        var count = 0
        var searchFrom = 0
        while (true) {
          val found = lowerText.indexOf(lowerQ, searchFrom)
          if (found < 0) break
          count++
          searchFrom = found + lowerQ.length
        }
        val start = (idx - 36).coerceAtLeast(0)
        val end = (idx + clean.length + 64).coerceAtMost(pageText.length)
        val snippet = pageText.substring(start, end).replace('\n', ' ').trim()
        hits += PdfSearchHit(pageIndex = p, snippet = snippet, matchCount = count)
      }
    }
    hits
  }

  fun close() {
    bitmapCache.evictAll()
    runCatching { renderer.close() }
    runCatching { pfd.close() }
    runCatching { pdDocument?.close() }
    pdDocument = null
    runCatching { tempFile?.delete() }
  }
}

class PdfReaderEngine(private val context: Context) {

  suspend fun openSession(uri: Uri): Result<PdfSession> = withContext(Dispatchers.IO) {
    runCatching {
      // Try direct file descriptor first; fall back to copying into temp file if provider doesn't support seekable FD
      var tempFile: File? = null
      val directPfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
      val pfd = if (directPfd != null) {
        try {
          val testRenderer = PdfRenderer(directPfd)
          return@runCatching PdfSession(context, uri, null, directPfd, testRenderer)
        } catch (_: Exception) {
          runCatching { directPfd.close() }
          null
        }
      } else null

      if (pfd == null) {
        val tempDir = File(context.cacheDir, "paperly_temp").apply { mkdirs() }
        tempFile = File(tempDir, "pdf_session_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
          FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: error("Unable to open PDF stream.")
        val filePfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(filePfd)
        PdfSession(context, uri, tempFile, filePfd, renderer)
      } else {
        error("Unreachable")
      }
    }
  }
}
