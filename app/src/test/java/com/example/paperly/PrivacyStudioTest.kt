package com.example.paperly

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.paperly.engines.image.ImageFormatOption
import com.example.paperly.engines.privacy.BlurStyle
import com.example.paperly.engines.privacy.ImagePrivacyEngine
import com.example.paperly.engines.privacy.LocalSensitiveDetector
import com.example.paperly.engines.privacy.NormPoint
import com.example.paperly.engines.privacy.NormRect
import com.example.paperly.engines.privacy.PdfPrivacyConfig
import com.example.paperly.engines.privacy.PdfPrivacyEngine
import com.example.paperly.engines.privacy.PlaceholderPreset
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.engines.privacy.PrivacyOperation
import com.example.paperly.engines.privacy.PrivacySelectionShape
import com.example.paperly.engines.privacy.TextReplacementConfig
import com.example.paperly.engines.privacy.VerificationStatus
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyStudioTest {

  private lateinit var context: Context
  private lateinit var detector: LocalSensitiveDetector
  private lateinit var imagePrivacyEngine: ImagePrivacyEngine
  private lateinit var pdfPrivacyEngine: PdfPrivacyEngine

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    PDFBoxResourceLoader.init(context)
    detector = LocalSensitiveDetector()
    imagePrivacyEngine = ImagePrivacyEngine(context)
    pdfPrivacyEngine = PdfPrivacyEngine(context, detector, imagePrivacyEngine)
  }

  @Test
  fun `image permanent redaction flattens opaque pixels strips EXIF and verifies output`() = runBlocking {
    val srcFile = File(context.cacheDir, "test_privacy_src.jpg")
    val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
      val c = Canvas(this)
      c.drawColor(Color.WHITE)
      val p = android.graphics.Paint().apply { color = Color.RED }
      c.drawRect(40f, 40f, 180f, 140f, p) // sensitive red block at top-left
    }
    FileOutputStream(srcFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    bmp.recycle()

    // Embed sensitive EXIF metadata to confirm stripping
    val srcExif = ExifInterface(srcFile.absolutePath)
    srcExif.setAttribute(ExifInterface.TAG_MAKE, "SensitiveCameraMake")
    srcExif.setAttribute(ExifInterface.TAG_USER_COMMENT, "PrivateLocationNote")
    srcExif.saveAttributes()

    val ops = listOf(
      PrivacyOperation(
        id = "op_rect",
        mode = PrivacyEditMode.PERMANENT_REDACTION,
        shape = PrivacySelectionShape.RECTANGLE,
        rect = NormRect(0.08f, 0.10f, 0.48f, 0.50f),
        fillColorArgb = Color.BLACK
      ),
      PrivacyOperation(
        id = "op_blur",
        mode = PrivacyEditMode.BLUR_PIXELATE,
        shape = PrivacySelectionShape.ELLIPSE,
        rect = NormRect(0.60f, 0.55f, 0.90f, 0.85f),
        blurStyle = BlurStyle.PIXELATE,
        mosaicBlockSize = 16
      ),
      PrivacyOperation(
        id = "op_rep",
        mode = PrivacyEditMode.REPLACE_ANONYMISE,
        shape = PrivacySelectionShape.RECTANGLE,
        rect = NormRect(0.55f, 0.10f, 0.92f, 0.30f),
        replacementConfig = TextReplacementConfig(text = PlaceholderPreset.PERSON.placeholder)
      )
    )

    val result = imagePrivacyEngine.applyAndExport(
      uri = Uri.fromFile(srcFile),
      operations = ops,
      outputFormat = ImageFormatOption.JPEG,
      quality = 94,
      suffix = "_redacted",
      stripExifMetadata = true
    )
    assertTrue(result.isSuccess)
    val exported = result.getOrThrow()
    assertTrue(exported.outputFile.name.endsWith("_redacted.jpg"))
    assertTrue(exported.verificationReport.verifiedSafeToShare)

    val exifCheck = exported.verificationReport.checks.first { it.itemName.contains("EXIF") }
    assertEquals(VerificationStatus.REMOVED_AND_VERIFIED, exifCheck.status)

    val redactionCheck = exported.verificationReport.checks.first { it.itemName.contains("Permanent redaction") }
    assertEquals(VerificationStatus.REMOVED_AND_VERIFIED, redactionCheck.status)

    val blurCheck = exported.verificationReport.checks.first { it.itemName.contains("Blur") }
    assertEquals(VerificationStatus.RETAINED_INTENTIONALLY, blurCheck.status)
    assertTrue(exported.verificationReport.warnings.any { it.contains("visual concealment", ignoreCase = true) })

    // Verify original file untouched
    assertTrue(srcFile.exists())
    val origExifAfter = ExifInterface(srcFile.absolutePath)
    assertEquals("SensitiveCameraMake", origExifAfter.getAttribute(ExifInterface.TAG_MAKE))
    exported.previewBitmap.recycle()
  }

  @Test
  fun `pdf permanent redaction and search replacement strip underlying text metadata and annotations`() = runBlocking {
    val srcPdf = File(context.cacheDir, "test_sensitive_doc.pdf")
    PDDocument().use { doc ->
      doc.documentInformation.author = "Confidential Author"
      doc.documentInformation.title = "Private Tax Filing"
      val page = PDPage(PDRectangle.A4)
      doc.addPage(page)
      val annot = PDAnnotationText().apply {
        contents = "Internal secret comment"
        rectangle = PDRectangle(50f, 50f, 40f, 40f)
      }
      page.annotations = listOf(annot)
      PDPageContentStream(doc, page).use { cs ->
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(50f, 750f)
        cs.showText("Client: Alice Smith")
        cs.newLineAtOffset(0f, -24f)
        cs.showText("SSN: 123-45-6789")
        cs.newLineAtOffset(0f, -24f)
        cs.showText("Email: alice@secret.org")
        cs.newLineAtOffset(0f, -24f)
        cs.showText("Note: KeepThisPublicLine intact")
        cs.endText()
      }
      doc.save(srcPdf)
    }

    val pdfUri = Uri.fromFile(srcPdf)
    val inspect = pdfPrivacyEngine.inspectPdfForPrivacy(pdfUri, listOf("Alice Smith")).getOrThrow()
    assertTrue(inspect.hasTextLayer)
    assertTrue(inspect.hasMetadata)
    assertEquals(1, inspect.annotationCount)
    assertTrue(inspect.suggestions.any { it.matchedText.contains("alice@secret.org") })
    assertTrue(inspect.suggestions.any { it.matchedText.contains("123-45-6789") })

    val ssnHits = pdfPrivacyEngine.searchOccurrences(pdfUri, "123-45-6789").getOrThrow()
    assertEquals(1, ssnHits.size)

    val ops = mutableListOf<PrivacyOperation>()
    ssnHits.first().rects.forEach { r ->
      ops.add(
        PrivacyOperation(
          id = "redact_ssn",
          pageIndex = 0,
          mode = PrivacyEditMode.PERMANENT_REDACTION,
          shape = PrivacySelectionShape.RECTANGLE,
          rect = r,
          fillColorArgb = Color.BLACK,
          sourceTextToVerify = "123-45-6789"
        )
      )
    }
    val emailSug = inspect.suggestions.first { it.matchedText.contains("alice@secret.org") }
    ops.add(
      PrivacyOperation(
        id = "replace_email",
        pageIndex = 0,
        mode = PrivacyEditMode.REPLACE_ANONYMISE,
        shape = PrivacySelectionShape.RECTANGLE,
        rect = emailSug.rect,
        replacementConfig = TextReplacementConfig(text = PlaceholderPreset.EMAIL.placeholder),
        sourceTextToVerify = emailSug.matchedText
      )
    )

    val exportRes = pdfPrivacyEngine.applyAndExport(
      uri = pdfUri,
      config = PdfPrivacyConfig(
        operations = ops,
        stripMetadata = true,
        stripAnnotations = true,
        stripFormFields = true,
        stripAttachments = true,
        stripHiddenLayers = true,
        outputSuffix = "_redacted"
      )
    )
    assertTrue(exportRes.isSuccess)
    val exported = exportRes.getOrThrow()
    assertTrue(exported.verificationReport.verifiedSafeToShare)

    val termCheck = exported.verificationReport.checks.first { it.itemName.contains("Redacted text strings") }
    assertEquals(VerificationStatus.REMOVED_AND_VERIFIED, termCheck.status)

    // Independently inspect exported PDF text layer to prove true removal
    PDDocument.load(exported.outputFile).use { outDoc ->
      val text = PDFTextStripper().getText(outDoc)
      assertFalse("SSN must be permanently stripped", text.contains("123-45-6789"))
      assertFalse("Email must be permanently stripped", text.contains("alice@secret.org"))
      assertTrue("Unredacted text should remain intact", text.contains("KeepThisPublicLine"))
      assertTrue("Replacement placeholder should be present", text.contains(PlaceholderPreset.EMAIL.placeholder))
      assertEquals(0, outDoc.getPage(0).annotations.size)
      assertEquals(null, outDoc.documentInformation.author)
    }

    // Confirm original source PDF remains completely untouched
    PDDocument.load(srcPdf).use { origDoc ->
      val origText = PDFTextStripper().getText(origDoc)
      assertTrue(origText.contains("123-45-6789"))
      assertEquals("Confidential Author", origDoc.documentInformation.author)
    }
  }

  @Test
  fun `app has zero internet permission for offline privacy compliance`() {
    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
    assertFalse("Privacy Studio must remain 100% offline", perms.contains(android.Manifest.permission.INTERNET))
  }
}
