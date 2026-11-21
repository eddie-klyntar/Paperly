package com.example.paperly.core

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PaperlyApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    runCatching { PDFBoxResourceLoader.init(applicationContext) }
    container = AppContainer(this)
  }
}
