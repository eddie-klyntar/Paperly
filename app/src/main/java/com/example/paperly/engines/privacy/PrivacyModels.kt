package com.example.paperly.engines.privacy

import android.graphics.Color
import java.io.File

enum class PrivacyEditMode(val label: String, val subtitle: String) {
  PERMANENT_REDACTION(
    label = "Permanent Redaction",
    subtitle = "Irreversibly removes selected content and metadata from the exported copy."
  ),
  BLUR_PIXELATE(
    label = "Blur & Pixelate",
    subtitle = "Visual concealment only. Not equivalent to irreversible security redaction."
  ),
  REPLACE_ANONYMISE(
    label = "Replace & Anonymise",
    subtitle = "Permanently removes originals and inserts non-identifying fictional placeholders."
  )
}

enum class PrivacySelectionShape(val label: String) {
  RECTANGLE("Rectangle"),
  ELLIPSE("Circle / Ellipse"),
  FREEHAND("Freehand"),
  BRUSH("Brush")
}

enum class BlurStyle(val label: String) {
  GAUSSIAN_BLUR("Gaussian Blur"),
  PIXELATE("Mosaic Pixelate")
}

enum class PdfRedactionStrategy(val label: String, val description: String) {
  CONTENT_STREAM_REWRITE(
    label = "True Vector & Text Rewrite",
    description = "Strips matching text operators, embedded image pixels, annotations, forms, metadata and incremental history in a full rewrite."
  ),
  RASTERIZE_AFFECTED_PAGES(
    label = "Safe Page Rasterisation",
    description = "Converts affected pages to flattened images, destroying underlying text/vectors on those pages (text selection lost on rasterised pages)."
  )
}

enum class PlaceholderPreset(val placeholder: String, val label: String) {
  PERSON("[PERSON A]", "Person Name"),
  EMAIL("user@example.invalid", "Email Address"),
  USERNAME("[USERNAME]", "Username"),
  ACCOUNT("XXXX XXXX 1234", "Account / Card"),
  PHONE("+XX XXXXX XXXXX", "Phone Number"),
  PASSWORD("[REDACTED]", "Password / Secret")
}

enum class TextAlignOption(val label: String) {
  LEFT("Left"),
  CENTER("Centre"),
  RIGHT("Right")
}

enum class ReplacementFontStyle(val label: String) {
  SANS("Sans Serif"),
  SERIF("Serif"),
  MONO("Monospace")
}

data class TextReplacementConfig(
  val text: String = PlaceholderPreset.PASSWORD.placeholder,
  val fontSizeSp: Float = 13f,
  val fontStyle: ReplacementFontStyle = ReplacementFontStyle.SANS,
  val textColorArgb: Int = Color.BLACK,
  val backgroundColorArgb: Int = 0xFFF1F5F9.toInt(),
  val alignment: TextAlignOption = TextAlignOption.CENTER
)

data class NormPoint(val x: Float, val y: Float)

data class NormRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float
) {
  val width: Float get() = (right - left).coerceAtLeast(0f)
  val height: Float get() = (bottom - top).coerceAtLeast(0f)

  fun normalized(): NormRect {
    val l = minOf(left, right).coerceIn(0f, 1f)
    val t = minOf(top, bottom).coerceIn(0f, 1f)
    val r = maxOf(left, right).coerceIn(0f, 1f)
    val b = maxOf(top, bottom).coerceIn(0f, 1f)
    return NormRect(l, t, r, b)
  }

  fun intersects(other: NormRect, padding: Float = 0f): Boolean {
    val a = normalized()
    val b = other.normalized()
    return a.left <= b.right + padding &&
      a.right >= b.left - padding &&
      a.top <= b.bottom + padding &&
      a.bottom >= b.top - padding
  }
}

data class PrivacyOperation(
  val id: String,
  val pageIndex: Int = 0,
  val mode: PrivacyEditMode = PrivacyEditMode.PERMANENT_REDACTION,
  val shape: PrivacySelectionShape = PrivacySelectionShape.RECTANGLE,
  val rect: NormRect,
  val points: List<NormPoint> = emptyList(),
  val brushWidthRatio: Float = 0.035f,
  val blurStyle: BlurStyle = BlurStyle.GAUSSIAN_BLUR,
  val blurRadius: Int = 18,
  val mosaicBlockSize: Int = 24,
  val fillColorArgb: Int = Color.BLACK,
  val optionalLabel: String = "",
  val replacementConfig: TextReplacementConfig = TextReplacementConfig(),
  val sourceTextToVerify: String = ""
)

enum class SensitiveCategory(val label: String) {
  EMAIL("Email"),
  PHONE("Phone"),
  ACCOUNT_NUMBER("Account / Card"),
  ID_NUMBER("ID / Tax Number"),
  ADDRESS("Street Address"),
  FACE("Face Region"),
  CUSTOM_KEYWORD("Custom Keyword")
}

data class SensitiveSuggestion(
  val id: String,
  val category: SensitiveCategory,
  val pageIndex: Int,
  val rect: NormRect,
  val matchedText: String,
  val displayHint: String,
  val recommendedPlaceholder: PlaceholderPreset,
  val selected: Boolean = false
)

enum class VerificationStatus(val label: String) {
  REMOVED_AND_VERIFIED("Removed and verified"),
  RETAINED_INTENTIONALLY("Retained intentionally"),
  NOT_DETECTED("Not detected"),
  UNSUPPORTED("Unsupported"),
  REMOVAL_ATTEMPTED_UNVERIFIED("Removal attempted but unverified"),
  REMOVAL_FAILED("Removal failed")
}

data class PrivacyVerificationCheck(
  val itemName: String,
  val status: VerificationStatus,
  val detail: String
)

data class PrivacyVerificationReport(
  val outputFile: File,
  val displayName: String,
  val originalBytes: Long,
  val outputBytes: Long,
  val summaryText: String,
  val checks: List<PrivacyVerificationCheck>,
  val verifiedSafeToShare: Boolean,
  val warnings: List<String> = emptyList()
)

data class PdfSearchOccurrence(
  val id: String,
  val pageIndex: Int,
  val matchedText: String,
  val snippet: String,
  val rects: List<NormRect>,
  val selected: Boolean = false
)

data class PdfPageWordBox(
  val text: String,
  val rect: NormRect
)
