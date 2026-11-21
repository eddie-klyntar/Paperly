package com.example.paperly

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.paperly.engines.image.ImageFitMode
import com.example.paperly.engines.image.ImageFormatOption
import com.example.paperly.engines.image.ImageProcessConfig
import com.example.paperly.engines.image.ImageWorkshopEngine
import com.example.paperly.engines.image.PdfOrientationOption
import com.example.paperly.engines.image.PdfPageSizePreset
import com.example.paperly.engines.pdf.PdfReaderEngine
import com.example.paperly.engines.pdfworkshop.AnnotationTool
import com.example.paperly.engines.pdfworkshop.NormalizedPoint
import com.example.paperly.engines.pdfworkshop.PageEditDescriptor
import com.example.paperly.engines.pdfworkshop.PdfAnnotationStroke
import com.example.paperly.engines.pdfworkshop.PdfCompressionPreset
import com.example.paperly.engines.pdfworkshop.PdfWorkshopEngine
import com.example.paperly.engines.spreadsheet.SpreadsheetEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnginesIntegrationTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    runCatching { PDFBoxResourceLoader.init(context) }
  }

  @Test
  fun `merged manifest contains no internet permission`() {
    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
    assertFalse("INTERNET permission must not be present", perms.contains(Manifest.permission.INTERNET))
  }

  @Test
  fun `spreadsheet engine parses csv and xlsx with formulas and search`() = runBlocking {
    val engine = SpreadsheetEngine(context)

    val csvFile = File(context.cacheDir, "sample.csv")
    csvFile.writeText("Item,Qty,Price\nNotebook,2,4.50\nPen,5,1.25\n")
    val csvRes = engine.loadWorkbook(Uri.fromFile(csvFile), "sample.csv").getOrThrow()
    assertEquals("CSV", csvRes.formatLabel)
    assertEquals(3, csvRes.worksheets.first().rows.size)
    assertEquals("Notebook", csvRes.worksheets.first().rows[1][0].displayText)

    // Build minimal valid XLSX zip
    val xlsxFile = File(context.cacheDir, "budget.xlsx")
    ZipOutputStream(FileOutputStream(xlsxFile)).use { zos ->
      fun put(name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray())
        zos.closeEntry()
      }
      put("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Summary" sheetId="1" r:id="rId1"/></sheets></workbook>""")
      put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
      put("xl/sharedStrings.xml", """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Total</t></si></sst>""")
      put("xl/worksheets/sheet1.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><f>10+20</f><v>30</v></c></row></sheetData></worksheet>""")
    }

    val xlsxRes = engine.loadWorkbook(Uri.fromFile(xlsxFile), "budget.xlsx").getOrThrow()
    assertEquals("XLSX", xlsxRes.formatLabel)
    assertEquals("Summary", xlsxRes.worksheets.first().name)
    assertEquals("Total", xlsxRes.worksheets.first().rows[0][0].displayText)
    assertEquals("30", xlsxRes.worksheets.first().rows[0][1].displayText)
    assertEquals("=10+20", xlsxRes.worksheets.first().rows[0][1].formula)

    val hits = engine.searchWorkbook(xlsxRes, "Total")
    assertEquals(1, hits.size)
  }

  @Test
  fun `image workshop resizes compresses warns on transparency and exports pdf`() = runBlocking {
    val engine = ImageWorkshopEngine(context)
    val bmp = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.TRANSPARENT)
      for (x in 20..180) setPixel(x, 60, Color.BLUE)
    }
    val pngFile = File(context.cacheDir, "logo.png")
    FileOutputStream(pngFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()

    val uri = Uri.fromFile(pngFile)
    val inspected = engine.inspectImage(uri).getOrThrow()
    assertEquals("PNG", inspected.detectedFormat)
    assertTrue(inspected.hasAlpha)

    val converted = engine.processSingle(
      uri,
      ImageProcessConfig(targetWidth = 100, maintainAspectRatio = true, format = ImageFormatOption.JPEG, quality = 75)
    ).getOrThrow()
    assertTrue(converted.transparencyWarning)
    assertEquals(100, converted.width)
    assertTrue(converted.outputFile.exists())

    val batch = engine.processBatch(listOf(uri), ImageProcessConfig(format = ImageFormatOption.WEBP, quality = 80)).toList()
    assertEquals(1, batch.size)
    assertNotNull(batch.first().result)

    val pdfOut = engine.createPdfFromImages(
      uris = listOf(uri),
      pageSizePreset = PdfPageSizePreset.A4,
      orientation = PdfOrientationOption.AUTO,
      marginPt = 16,
      fitMode = ImageFitMode.FIT,
      outputFileName = "img_bundle"
    ).getOrThrow()
    assertTrue(pdfOut.exists() && pdfOut.length() > 100)
  }

  @Test
  fun `pdf workshop merge split pages compress annotate and reader search work offline`() = runBlocking {
    val pdfA = File(context.cacheDir, "docA.pdf")
    val pdfB = File(context.cacheDir, "docB.pdf")
    createSamplePdf(pdfA, "Hello Paperly Workspace")
    createSamplePdf(pdfB, "Second Document Page")

    val workshop = PdfWorkshopEngine(context)
    val merged = workshop.mergePdfs(listOf(Uri.fromFile(pdfA), Uri.fromFile(pdfB)), "merged_out").getOrThrow()
    assertTrue(merged.exists())

    PDDocument.load(merged).use { doc ->
      assertEquals(2, doc.numberOfPages)
      val extracted = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
      assertTrue(extracted.contains("Paperly Workspace"))
      assertTrue(extracted.contains("Second Document Page"))
    }

    val splits = workshop.splitPdfEveryN(Uri.fromFile(merged), 1, "split_out").getOrThrow()
    assertEquals(2, splits.size)

    val reordered = workshop.applyPageOperations(
      Uri.fromFile(merged),
      listOf(PageEditDescriptor("1", 1, 90), PageEditDescriptor("0", 0, 0)),
      "reordered_out"
    ).getOrThrow()
    PDDocument.load(reordered).use { doc ->
      assertEquals(2, doc.numberOfPages)
      assertEquals(90, doc.getPage(0).rotation)
    }

    val compressed = workshop.compressPdf(Uri.fromFile(merged), PdfCompressionPreset.BALANCED, true, "comp_out").getOrThrow()
    assertTrue(compressed.outputFile.exists())

    val annotated = workshop.saveAnnotatedPdf(
      Uri.fromFile(pdfA),
      listOf(
        PdfAnnotationStroke(
          pageIndex = 0,
          tool = AnnotationTool.FREEHAND,
          colorArgb = Color.RED,
          strokeWidthRatio = 0.005f,
          points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.5f, 0.5f))
        )
      ),
      "annotated_out"
    ).getOrThrow()
    assertTrue(annotated.exists())
  }

  private fun createSamplePdf(file: File, text: String) {
    PDDocument().use { doc ->
      val page = PDPage()
      doc.addPage(page)
      PDPageContentStream(doc, page).use { cs ->
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 14f)
        cs.newLineAtOffset(72f, 700f)
        cs.showText(text)
        cs.endText()
      }
      doc.save(file)
    }
  }
}
