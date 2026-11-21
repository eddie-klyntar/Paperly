package com.example.paperly.core

import android.content.Context
import com.example.paperly.data.db.PaperlyDatabase
import com.example.paperly.data.prefs.SettingsStore
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.image.ImageWorkshopEngine
import com.example.paperly.engines.pdf.PdfReaderEngine
import com.example.paperly.engines.pdfworkshop.PdfWorkshopEngine
import com.example.paperly.engines.spreadsheet.SpreadsheetEngine

class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val database: PaperlyDatabase by lazy { PaperlyDatabase.build(appContext) }
  val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
  val documentRepository: DocumentRepository by lazy {
    DocumentRepository(
      context = appContext,
      recentDao = database.recentDocumentDao(),
      bookmarkDao = database.bookmarkDao(),
      customThemeDao = database.customThemeDao()
    )
  }

  val pdfReaderEngine: PdfReaderEngine by lazy { PdfReaderEngine(appContext) }
  val spreadsheetEngine: SpreadsheetEngine by lazy { SpreadsheetEngine(appContext) }
  val imageWorkshopEngine: ImageWorkshopEngine by lazy { ImageWorkshopEngine(appContext) }
  val pdfWorkshopEngine: PdfWorkshopEngine by lazy { PdfWorkshopEngine(appContext) }
}
