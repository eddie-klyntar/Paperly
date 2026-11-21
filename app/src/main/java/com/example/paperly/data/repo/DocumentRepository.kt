package com.example.paperly.data.repo

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.paperly.data.db.BookmarkDao
import com.example.paperly.data.db.BookmarkEntity
import com.example.paperly.data.db.CustomThemeDao
import com.example.paperly.data.db.CustomThemeEntity
import com.example.paperly.data.db.DocumentKind
import com.example.paperly.data.db.PrivacyProjectDao
import com.example.paperly.data.db.RecentDocumentDao
import com.example.paperly.data.db.RecentDocumentEntity
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.theme.PaperlyThemeSpec
import com.example.paperly.theme.ThemeJsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class DocumentMetadata(
  val uri: Uri,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val kind: DocumentKind
)

class DocumentRepository(
  private val context: Context,
  private val recentDao: RecentDocumentDao,
  private val bookmarkDao: BookmarkDao,
  private val customThemeDao: CustomThemeDao,
  private val privacyProjectDao: PrivacyProjectDao
) {
  val recentDocuments: Flow<List<RecentDocumentEntity>> = recentDao.observeAll()
  val favoriteDocuments: Flow<List<RecentDocumentEntity>> = recentDao.observeFavorites()
  val recentPrivacyProjects: Flow<List<RecentPrivacyProjectEntity>> = privacyProjectDao.observeAll()

  val customThemes: Flow<List<PaperlyThemeSpec>> = customThemeDao.observeAll().map { entities ->
    entities.mapNotNull { entity ->
      runCatching { ThemeJsonCodec.decode(entity.jsonConfig, entity.id).copy(isCustom = true, name = entity.name) }.getOrNull()
    }
  }

  fun observeBookmarks(uri: String): Flow<List<BookmarkEntity>> = bookmarkDao.observeForDocument(uri)

  suspend fun addBookmark(uri: String, pageIndex: Int, label: String) = withContext(Dispatchers.IO) {
    bookmarkDao.insert(BookmarkEntity(documentUri = uri, pageIndex = pageIndex, label = label))
  }

  suspend fun removeBookmark(id: Long) = withContext(Dispatchers.IO) {
    bookmarkDao.deleteById(id)
  }

  suspend fun removeBookmarkAtPage(uri: String, pageIndex: Int) = withContext(Dispatchers.IO) {
    bookmarkDao.deleteAtPage(uri, pageIndex)
  }

  suspend fun saveCustomTheme(spec: PaperlyThemeSpec) = withContext(Dispatchers.IO) {
    val customSpec = spec.copy(isCustom = true)
    customThemeDao.upsert(
      CustomThemeEntity(
        id = customSpec.id,
        name = customSpec.name,
        collectionName = customSpec.collection.name,
        jsonConfig = ThemeJsonCodec.encode(customSpec),
        updatedAt = System.currentTimeMillis()
      )
    )
  }

  suspend fun deleteCustomTheme(id: String) = withContext(Dispatchers.IO) {
    customThemeDao.deleteById(id)
  }

  fun persistUriPermission(uri: Uri) {
    try {
      context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
      // Non-persistable URIs (e.g. FileProvider exports or one-time share intents) still work for the session
    } catch (_: Exception) {
    }
  }

  suspend fun inspectUri(uri: Uri): DocumentMetadata = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    var size = -1L
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
      val file = uri.path?.let { File(it) }
      if (file != null && file.exists()) {
        name = file.name
        size = file.length()
      }
    } else {
      runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
          if (c.moveToFirst()) {
            val nIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (nIdx >= 0 && !c.isNull(nIdx)) name = c.getString(nIdx)
            if (sIdx >= 0 && !c.isNull(sIdx)) size = c.getLong(sIdx)
          }
        }
      }
    }
    val rawMime = resolver.getType(uri) ?: ""
    val kind = detectKind(uri, name, rawMime)
    val mime = rawMime.ifBlank { defaultMimeFor(kind) }
    DocumentMetadata(uri = uri, displayName = name, mimeType = mime, sizeBytes = size, kind = kind)
  }

  private fun detectKind(uri: Uri, name: String, mime: String): DocumentKind {
    val lower = name.lowercase()
    val m = mime.lowercase()
    // Sniff magic bytes first when possible
    val header = ByteArray(8)
    val readCount = runCatching {
      context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
    }.getOrDefault(0)
    if (readCount >= 4) {
      // %PDF
      if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
        return DocumentKind.PDF
      }
      // OLE2 (legacy XLS) D0 CF 11 E0
      if (header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
        return DocumentKind.XLS
      }
      // PNG 89 50 4E 47 or JPEG FF D8 FF
      if ((header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte()) ||
        (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte())
      ) {
        return DocumentKind.IMAGE
      }
    }
    return when {
      lower.endsWith(".pdf") || m == "application/pdf" -> DocumentKind.PDF
      lower.endsWith(".xlsx") || m.contains("spreadsheetml") -> DocumentKind.XLSX
      lower.endsWith(".xls") || m == "application/vnd.ms-excel" -> DocumentKind.XLS
      lower.endsWith(".tsv") || m == "text/tab-separated-values" -> DocumentKind.TSV
      lower.endsWith(".csv") || m.contains("csv") -> DocumentKind.CSV
      m.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
        lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".bmp") -> DocumentKind.IMAGE
      readCount >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() -> DocumentKind.XLSX
      else -> DocumentKind.PDF
    }
  }

  private fun defaultMimeFor(kind: DocumentKind): String = when (kind) {
    DocumentKind.PDF -> "application/pdf"
    DocumentKind.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    DocumentKind.XLS -> "application/vnd.ms-excel"
    DocumentKind.CSV -> "text/csv"
    DocumentKind.TSV -> "text/tab-separated-values"
    DocumentKind.IMAGE -> "image/*"
  }

  suspend fun recordOpenedDocument(meta: DocumentMetadata, pageOrSheetCount: Int): RecentDocumentEntity =
    withContext(Dispatchers.IO) {
      val existing = recentDao.findByUri(meta.uri.toString())
      val updated = RecentDocumentEntity(
        uriString = meta.uri.toString(),
        displayName = meta.displayName,
        fileType = meta.kind.name,
        mimeType = meta.mimeType,
        fileSizeBytes = meta.sizeBytes,
        pageOrSheetCount = pageOrSheetCount.coerceAtLeast(existing?.pageOrSheetCount ?: 0),
        lastOpenedAt = System.currentTimeMillis(),
        lastPageIndex = existing?.lastPageIndex ?: 0,
        lastSheetIndex = existing?.lastSheetIndex ?: 0,
        lastZoom = existing?.lastZoom ?: 1f,
        isFavorite = existing?.isFavorite ?: false
      )
      recentDao.upsert(updated)
      updated
    }

  suspend fun findRecent(uriString: String): RecentDocumentEntity? = withContext(Dispatchers.IO) {
    recentDao.findByUri(uriString)
  }

  suspend fun updatePdfPosition(uriString: String, pageIndex: Int, zoom: Float) = withContext(Dispatchers.IO) {
    recentDao.updatePdfProgress(uriString, pageIndex, zoom, System.currentTimeMillis())
  }

  suspend fun updateSheetPosition(uriString: String, sheetIndex: Int) = withContext(Dispatchers.IO) {
    recentDao.updateSheetProgress(uriString, sheetIndex, System.currentTimeMillis())
  }

  suspend fun toggleDocumentFavorite(uriString: String, currentlyFavorite: Boolean) = withContext(Dispatchers.IO) {
    recentDao.setFavorite(uriString, !currentlyFavorite)
  }

  suspend fun removeHistoryEntry(uriString: String) = withContext(Dispatchers.IO) {
    recentDao.deleteByUri(uriString)
  }

  suspend fun clearRecentHistory() = withContext(Dispatchers.IO) {
    recentDao.clearAll()
  }

  suspend fun recordPrivacyProject(project: RecentPrivacyProjectEntity) = withContext(Dispatchers.IO) {
    privacyProjectDao.upsert(project)
  }

  suspend fun deletePrivacyProject(id: String) = withContext(Dispatchers.IO) {
    privacyProjectDao.deleteById(id)
  }

  suspend fun clearPrivacyProjects() = withContext(Dispatchers.IO) {
    privacyProjectDao.clearAll()
  }

  fun workspaceExportsDir(): File = File(context.filesDir, "paperly_exports").apply { mkdirs() }

  fun workspaceTempDir(): File = File(context.cacheDir, "paperly_temp").apply { mkdirs() }

  suspend fun clearTemporaryCache(): Long = withContext(Dispatchers.IO) {
    var freed = 0L
    workspaceTempDir().listFiles()?.forEach { f ->
      freed += f.length()
      f.deleteRecursively()
    }
    context.cacheDir.listFiles()?.filter { it.name.startsWith("paperly_") }?.forEach { f ->
      freed += f.length()
      f.deleteRecursively()
    }
    freed
  }

  fun createShareIntent(file: File, mimeType: String, chooserTitle: String): Intent {
    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, contentUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, chooserTitle)
  }

  fun contentUriForFile(file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
