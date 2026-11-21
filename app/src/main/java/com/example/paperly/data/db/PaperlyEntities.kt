package com.example.paperly.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DocumentKind(val label: String) {
  PDF("PDF"),
  XLSX("XLSX"),
  CSV("CSV"),
  TSV("TSV"),
  XLS("XLS"),
  IMAGE("Image");

  val isSpreadsheet: Boolean
    get() = this == XLSX || this == CSV || this == TSV || this == XLS
}

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
  @PrimaryKey val uriString: String,
  val displayName: String,
  val fileType: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val pageOrSheetCount: Int,
  val lastOpenedAt: Long,
  val lastPageIndex: Int = 0,
  val lastSheetIndex: Int = 0,
  val lastZoom: Float = 1f,
  val isFavorite: Boolean = false
) {
  val kind: DocumentKind
    get() = runCatching { DocumentKind.valueOf(fileType) }.getOrDefault(DocumentKind.PDF)
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val documentUri: String,
  val pageIndex: Int,
  val label: String,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_themes")
data class CustomThemeEntity(
  @PrimaryKey val id: String,
  val name: String,
  val collectionName: String,
  val jsonConfig: String,
  val updatedAt: Long = System.currentTimeMillis(),
  val isFavorite: Boolean = false
)

@Entity(tableName = "recent_privacy_projects")
data class RecentPrivacyProjectEntity(
  @PrimaryKey val id: String,
  val sourceUriString: String,
  val displayName: String,
  val sourceKind: String,
  val lastMode: String,
  val outputFilePath: String,
  val outputDisplayName: String,
  val verificationSummary: String,
  val verifiedSafe: Boolean,
  val operationCount: Int,
  val updatedAt: Long = System.currentTimeMillis()
)

