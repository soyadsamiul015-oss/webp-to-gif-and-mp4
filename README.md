# WebP Bulk Converter (offline Android app)

Converts .webp files (animated or static) to **GIF** and/or **MP4** in bulk, fully offline. FFmpeg is bundled inside the APK — no internet needed after install.

## Features
- Pick multiple .webp files at once, or pick a whole folder and auto-load every .webp in it
- **Share directly from your browser**: in Chrome, long-press a webp → Share → "WebP Bulk Converter" (works with multiple files too)
- Select All / Unselect All / Clear buttons
- Output format: GIF, MP4, or Both
- Results saved to **Downloads/WebpConverter**
- Per-file status (converting / done / failed)

## How to get the APK — Option A: GitHub Actions (no Android Studio needed)
1. Create a free GitHub account if you don't have one.
2. Create a new repository and upload ALL files in this folder (keep the folder structure, including the hidden `.github` folder).
3. Go to the **Actions** tab of your repo → the "Build APK" workflow runs automatically (or press "Run workflow").
4. Wait ~10-15 min (first build downloads FFmpeg, ~90 MB).
5. Open the finished run → download the **WebpBulkConverter-debug-apk** artifact → unzip → install `app-debug.apk` on your phone (allow "install from unknown sources").

## How to get the APK — Option B: Android Studio
1. Install Android Studio, open this folder as a project.
2. Let Gradle sync (it auto-downloads the FFmpeg AAR into `libs/` on first build).
3. Build → Build APK(s). The APK appears in `app/build/outputs/apk/debug/`.

## Notes
- APK will be large (~90-120 MB) because a full FFmpeg build is bundled — that's the price of true offline conversion.
- MP4 output uses H.264 (libx264, GPL build). Fine for personal use; if you ever ship this commercially, check GPL licensing.
- Static (non-animated) webp → MP4 produces a very short 1-frame video; GIF output handles static images fine.
- If the FFmpeg AAR download link ever dies, search GitHub for "ffmpeg-kit-full-gpl aar" mirrors and update the URL in `app/build.gradle`.
