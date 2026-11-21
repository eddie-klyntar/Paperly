# Paperly

**Your documents. Your workspace.**

Paperly is a complete, 100% offline-first Android document workspace built with **Kotlin**, **Jetpack Compose**, and **Material 3**. It brings four core utilities together in a single, cohesive, privacy-respecting application:

1. **PDF Reader** — Lazy page rendering (`PdfRenderer`), continuous vertical or horizontal single-page modes, pinch & double-tap zoom, page rotation, full-screen reading, warm/dark/grayscale reading appearances, text selection & copy, in-document search, bookmarks, outline (TOC), reading-position restoration, and honest scanned-PDF detection.
2. **Spreadsheet Viewer** — Read-only offline viewer for **XLSX**, **CSV**, **TSV**, and basic **XLS** text inspection, with virtualized 2D grid scrolling, worksheet tabs, sticky row/column headers, freeze panes, cell & range copying, formula inspection, formatting display, zoom, and search.
3. **Image Workshop** — Format detection via magic bytes, Exif auto-orientation, resizing (custom dimensions, aspect-ratio lock, percentage, presets), quality & target-KB compression, format conversion (JPEG, PNG, WebP) with transparency-loss warnings, batch processing queue, and multipage Image-to-PDF generation.
4. **PDF Workshop** — Non-destructive Merge, Split (ranges or every N pages), Organise Pages (reorder, rotate, duplicate, delete, extract), Compression (embedded bitmap JPEG recompression + metadata stripping, clearly marked as lossy), and Interactive Annotate & Sign (freehand, highlight, underline, text notes, shapes, and visual signature insertion).

### Visual Personalisation Engine (280+ Presets & Custom Theme Studio)
- **280+ locally bundled presets** across **40 distinct design collections** (Liquid Glass, Glassmorphism, Frosted Glass, Neumorphism, Material You, Windows Fluent, macOS-inspired, Pure AMOLED, Midnight Blue, Cyberpunk, Retro Terminal, Swiss Minimalism, Editorial, Vintage Paper, Typewriter, Brutalist, Scandinavian, Japanese Minimalism, Y2K, Vaporwave, Synthwave, Pastel, Monochrome, Organic Nature, Sunset Gradient, Ocean, Aurora, Industrial, Metallic, Holographic, Blueprint, Retro Computing, Academic, Modern Corporate, Minimal White, Dark Editorial, Warm Ivory, Soft Lavender, Clay, Botanical).
- Every collection alters **typography**, **surface material**, **background textures**, **corner/border/shadow geometry**, **navigation style**, and **motion behavior**.
- **Custom Theme Studio** with live screen previews for Home, PDF Reader, Spreadsheet Viewer, Image Workshop, and PDF Workshop, plus JSON import/export.

## Privacy & Offline Architecture
- **Zero `INTERNET` permission** in the merged application manifest (`tools:node="remove"` + no network dependencies).
- No analytics, no ads, no telemetry, no accounts, no remote font downloads, and no cloud processing.
- All fonts are bundled locally in `app/src/main/res/font/`.
- All operations preserve original documents via Android Storage Access Framework (SAF), Android Photo Picker, and `FileProvider` sharing.

## Format Support & Honest Limitations
- **PDFs:** Text selection and search require an embedded text layer; scanned/image-only PDFs are clearly identified without pretending to run OCR. Visual signature insertion draws strokes onto the PDF page and is not a cryptographic digital signature.
- **Spreadsheets:** Read-only inspection; XLSX displays cached values and formula expressions without recalculating complex formulas or executing macros/VBA. Legacy binary `.xls` (BIFF8) has limited offline string extraction support compared to `.xlsx`/`.csv`/`.tsv`.
- **Compression:** Image target file size is an objective that stops before destroying legibility. PDF compression recompresses embedded raster images lossily while preserving vector paths and fonts.

## Downloading the APK from GitHub Actions
1. Open the repository on GitHub and click the **Actions** tab.
2. Select the **Build Paperly APK** workflow (or click **Run workflow** via `workflow_dispatch`).
3. Click the latest completed workflow run.
4. Scroll down to the **Artifacts** section and download **`paperly-debug-apk`**.
5. Unzip the downloaded archive and install `app-debug.apk` on your Android device (Android 7.0+ / API 24+).

## Building Locally
```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
```
