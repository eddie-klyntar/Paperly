package com.example.paperly

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.paperly.data.prefs.SettingsStore
import com.example.paperly.engines.image.ImageFormatOption
import com.example.paperly.engines.image.ImageWorkshopEngine
import com.example.paperly.engines.pdf.PdfReaderEngine
import com.example.paperly.engines.privacy.BlurStyle
import com.example.paperly.engines.privacy.ImagePrivacyEngine
import com.example.paperly.engines.privacy.LocalSensitiveDetector
import com.example.paperly.engines.privacy.NormRect
import com.example.paperly.engines.privacy.PdfPrivacyConfig
import com.example.paperly.engines.privacy.PdfPrivacyEngine
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.engines.privacy.PrivacyOperation
import com.example.paperly.engines.privacy.PrivacySelectionShape
import com.example.paperly.engines.privacy.VerificationStatus
import com.example.paperly.engines.spreadsheet.SpreadsheetEngine
import com.example.paperly.ui.spreadsheet.resolveSpreadsheetCellTextColor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ReleaseStabilityAuditTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    runCatching { PDFBoxResourceLoader.init(context) }
  }

  @Test
  fun `release metadata is v0_1_0 and strictly offline with zero network permissions`() {
    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    assertEquals("0.1.0", pkgInfo.versionName)
    val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
    assertFalse("INTERNET must never be requested", perms.contains(Manifest.permission.INTERNET))
    assertFalse("ACCESS_NETWORK_STATE must never be requested", perms.contains(Manifest.permission.ACCESS_NETWORK_STATE))
  }

  @Test
  fun `engines reject empty and malformed inputs cleanly without crashing`() = runBlocking {
    val pdfEngine = PdfReaderEngine(context)
    val emptyPdf = File(context.cacheDir, "empty_audit.pdf").apply { writeBytes(ByteArray(0)) }
    val corruptPdf = File(context.cacheDir, "corrupt_audit.pdf").apply { writeText("NOT A VALID PDF HEADER") }

    val emptyPdfRes = pdfEngine.openSession(Uri.fromFile(emptyPdf))
    assertTrue("Empty PDF must fail cleanly", emptyPdfRes.isFailure)
    assertTrue(emptyPdfRes.exceptionOrNull()?.message?.isNotBlank() == true)

    val corruptPdfRes = pdfEngine.openSession(Uri.fromFile(corruptPdf))
    assertTrue("Corrupt PDF must fail cleanly", corruptPdfRes.isFailure)

    val privacyPdfEngine = PdfPrivacyEngine(context, LocalSensitiveDetector(), ImagePrivacyEngine(context))
    val emptyPrivacyPdfRes = privacyPdfEngine.applyAndExport(
      Uri.fromFile(emptyPdf),
      PdfPrivacyConfig(operations = emptyList())
    )
    assertTrue("Empty PDF privacy export must fail cleanly", emptyPrivacyPdfRes.isFailure)

    val sheetEngine = SpreadsheetEngine(context)
    val emptyCsv = File(context.cacheDir, "empty_sheet.csv").apply { writeBytes(ByteArray(0)) }
    val corruptXlsx = File(context.cacheDir, "corrupt_sheet.xlsx").apply { writeText("corrupt zip bytes") }
    assertTrue(sheetEngine.loadWorkbook(Uri.fromFile(emptyCsv), "empty_sheet.csv").isFailure)
    assertTrue(sheetEngine.loadWorkbook(Uri.fromFile(corruptXlsx), "corrupt_sheet.xlsx").isFailure)

    val imageWorkshop = ImageWorkshopEngine(context)
    val imagePrivacy = ImagePrivacyEngine(context)
    val corruptImg = File(context.cacheDir, "corrupt_img.jpg").apply { writeText("not an image") }
    assertTrue(imageWorkshop.inspectImage(Uri.fromFile(corruptImg)).isFailure)
    assertTrue(
      imagePrivacy.applyAndExport(
        uri = Uri.fromFile(corruptImg),
        operations = emptyList(),
        outputFormat = ImageFormatOption.JPEG
      ).isFailure
    )
  }

  @Test
  fun `spreadsheet parses large multi-sheet xlsx with frozen rows dates empty cells and dark theme contrast`() = runBlocking {
    val sheetEngine = SpreadsheetEngine(context)
    val xlsxFile = File(context.cacheDir, "large_audit.xlsx")

    val sheet1Xml = buildString {
      append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
      append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" state="frozen"/></sheetView></sheetViews>""")
      append("""<sheetData>""")
      append("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>""")
      for (r in 2..605) {
        if (r == 420) {
          // Include empty cell B420 and long text + target keyword in C420
          append("""<row r="$r"><c r="A$r"><v>45300</v></c><c r="C$r" t="inlineStr"><is><t>QuarterAuditTarget_Row420_ExtendedSummaryText</t></is></c></row>""")
        } else {
          append("""<row r="$r"><c r="A$r"><v>${r * 10}</v></c><c r="B$r"><f>A$r*2</f><v>${r * 20}</v></c></row>""")
        }
      }
      append("""</sheetData></worksheet>""")
    }

    ZipOutputStream(FileOutputStream(xlsxFile)).use { zos ->
      fun put(name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray())
        zos.closeEntry()
      }
      put(
        "xl/workbook.xml",
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Ledger" sheetId="1" r:id="rId1"/><sheet name="Notes" sheetId="2" r:id="rId2"/></sheets></workbook>"""
      )
      put(
        "xl/_rels/workbook.xml.rels",
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>"""
      )
      put(
        "xl/sharedStrings.xml",
        """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>ID</t></si><si><t>Double</t></si><si><t>Notes</t></si></sst>"""
      )
      put("xl/worksheets/sheet1.xml", sheet1Xml)
      put(
        "xl/worksheets/sheet2.xml",
        """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>SecondSheetReady</t></is></c></row></sheetData></worksheet>"""
      )
    }

    val wb = sheetEngine.loadWorkbook(Uri.fromFile(xlsxFile), "large_audit.xlsx").getOrThrow()
    assertEquals(2, wb.worksheets.size)
    val ledger = wb.worksheets[0]
    assertEquals("Ledger", ledger.name)
    assertEquals(1, ledger.freezeRows)
    assertEquals(605, ledger.rows.size)
    // Empty middle cell B420 (index 419, col 1) must be preserved as blank
    assertEquals("", ledger.rows[419][1].displayText)

    val hits = sheetEngine.searchWorkbook(wb, "QuarterAuditTarget_Row420")
    assertEquals(1, hits.size)
    assertEquals(0, hits.first().sheetIndex)
    assertEquals(419, hits.first().row)
    assertEquals(2, hits.first().col)

    // Contrast verification on shaded cells in dark theme
    val darkThemePrimaryText = Color(0xFFF8FAFC)
    val onLightFill = resolveSpreadsheetCellTextColor(
      isSelected = false,
      bgColorArgb = 0xFFE2E8F0,
      textColorArgb = null,
      fallbackThemeTextColor = darkThemePrimaryText
    )
    assertEquals("Light cell fill must get dark ink", Color(0xFF0F172A), onLightFill)

    val onDarkFill = resolveSpreadsheetCellTextColor(
      isSelected = false,
      bgColorArgb = 0xFF1E293B,
      textColorArgb = null,
      fallbackThemeTextColor = Color(0xFF0F172A)
    )
    assertEquals("Dark cell fill must get light ink", Color(0xFFF8FAFC), onDarkFill)

    val unshaded = resolveSpreadsheetCellTextColor(
      isSelected = false,
      bgColorArgb = null,
      textColorArgb = 0xFF000000,
      fallbackThemeTextColor = darkThemePrimaryText
    )
    assertEquals("Unshaded cell uses active theme text color to prevent dark-mode contrast issues", darkThemePrimaryText, unshaded)
  }

  @Test
  fun `privacy studio permanently redacts 90-deg rotated pdf and reports blur failure honestly`() = runBlocking {
    val rotatedPdf = File(context.cacheDir, "rotated_audit.pdf")
    PDDocument().use { doc ->
      val page = PDPage(PDRectangle.A4).apply { rotation = 90 }
      doc.addPage(page)
      PDPageContentStream(doc, page).use { cs ->
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(60f, 700f)
        cs.showText("SecretToken99")
        cs.newLineAtOffset(0f, -30f)
        cs.showText("PublicHeaderStay")
        cs.endText()
      }
      doc.save(rotatedPdf)
    }

    val pdfUri = Uri.fromFile(rotatedPdf)
    val detector = LocalSensitiveDetector()
    val imagePrivacy = ImagePrivacyEngine(context)
    val pdfPrivacy = PdfPrivacyEngine(context, detector, imagePrivacy)

    val hits = pdfPrivacy.searchOccurrences(pdfUri, "SecretToken99").getOrThrow()
    assertEquals(1, hits.size)
    val redactOps = hits.first().rects.mapIndexed { idx, r ->
      PrivacyOperation(
        id = "rot_redact_$idx",
        pageIndex = 0,
        mode = PrivacyEditMode.PERMANENT_REDACTION,
        shape = PrivacySelectionShape.RECTANGLE,
        rect = r,
        fillColorArgb = AndroidColor.BLACK,
        sourceTextToVerify = "SecretToken99"
      )
    }

    val exportRes = pdfPrivacy.applyAndExport(
      uri = pdfUri,
      config = PdfPrivacyConfig(
        operations = redactOps,
        stripMetadata = true,
        stripAnnotations = true,
        outputSuffix = "_rot_clean"
      )
    ).getOrThrow()

    assertTrue("Rotated PDF redaction should verify clean", exportRes.verificationReport.verifiedSafeToShare)
    PDDocument.load(exportRes.outputFile).use { outDoc ->
      val stripped = PDFTextStripper().getText(outDoc)
      val compact = stripped.replace(Regex("\\s+"), "")
      assertFalse("Rotated secret token must be permanently removed (text='$stripped')", compact.contains("SecretToken99"))
      assertTrue("Non-redacted text on rotated page must remain (text='$stripped')", compact.contains("PublicHeaderStay"))
    }

    // Blur/pixelate in Robolectric JVM where PdfRenderer is unavailable must report REMOVAL_FAILED honestly
    val blurOp = PrivacyOperation(
      id = "blur_fallback_check",
      pageIndex = 0,
      mode = PrivacyEditMode.BLUR_PIXELATE,
      shape = PrivacySelectionShape.RECTANGLE,
      rect = NormRect(0.1f, 0.1f, 0.5f, 0.3f),
      blurStyle = BlurStyle.GAUSSIAN_BLUR
    )
    val blurExport = pdfPrivacy.applyAndExport(
      uri = pdfUri,
      config = PdfPrivacyConfig(operations = listOf(blurOp), outputSuffix = "_rot_blur")
    ).getOrThrow()
    assertFalse(
      "Unavailable PdfRenderer rasterization must never silently report safe-to-share",
      blurExport.verificationReport.verifiedSafeToShare
    )
    assertTrue(
      blurExport.verificationReport.checks.any { it.status == VerificationStatus.REMOVAL_FAILED }
    )
  }

  @Test
  fun `privacy image export preserves original source file bytes and settings store persists preferences`() = runBlocking {
    val srcImg = File(context.cacheDir, "nondestructive_src.png")
    val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
      eraseColor(AndroidColor.CYAN)
    }
    FileOutputStream(srcImg).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    val originalBytes = srcImg.readBytes()

    val engine = ImagePrivacyEngine(context)
    val res = engine.applyAndExport(
      uri = Uri.fromFile(srcImg),
      operations = listOf(
        PrivacyOperation(
          id = "rect_1",
          mode = PrivacyEditMode.PERMANENT_REDACTION,
          shape = PrivacySelectionShape.RECTANGLE,
          rect = NormRect(0.1f, 0.1f, 0.4f, 0.4f)
        )
      ),
      outputFormat = ImageFormatOption.PNG,
      suffix = "_clean"
    ).getOrThrow()

    assertTrue(res.outputFile.exists())
    assertFalse(res.outputFile.absolutePath == srcImg.absolutePath)
    assertArrayEquals("Original image bytes must never be modified", originalBytes, srcImg.readBytes())
    res.previewBitmap.recycle()

    val settingsStore = SettingsStore(context)
    settingsStore.updatePdfDefaults(zoom = 1.5f, continuous = false)
    settingsStore.updateSpreadsheetDefaults(zoom = 1.25f, grid = false)
    val updated = settingsStore.settingsFlow.first()
    assertEquals(1.5f, updated.pdfDefaultZoom, 0.01f)
    assertFalse(updated.pdfContinuousScroll)
    assertEquals(1.25f, updated.sheetDefaultZoom, 0.01f)
    assertFalse(updated.sheetShowGridLines)
  }
}
