# VoiceNote AI Tutor (voice-note-ai-tutor)

**Language / 语言**: [简体中文](README.md) | [English](README_EN.md)

A local-first Android app for classroom/lecture recording: take photo markers during recording (with optional text notes), and after recording stops, marker photos are automatically sent to DeepSeek AI in chronological order with the prompt "explain the content in detail"; AI replies are saved via streaming. Supports playback with marker seeking, timetable-based naming, day-grouped list, zip backup export/import (hash-verified merge), recycle bin, and in-app updates.

## Features

- **Background recording with keep-alive**: foreground service + persistent notification (pause/resume/stop), keeps recording when screen is off or app is backgrounded; AAC 48 kHz stereo at the highest quality.
- **Interruption handling**: auto-pauses on phone calls / audio-focus loss and records the event, auto-resumes afterwards; the playback page shows interruption points.
- **Photo markers**: system camera / in-app camera (CameraX) / gallery → uCrop free-form cropping → text notes → multiple photos per marker; markers are written to the database immediately.
- **AI explanation**: DeepSeek-compatible API, images as base64 + prompt, SSE streaming shown live; on failure it skips the rest automatically, with "resend failed / resend all / resend one" options.
- **Playback page**: ExoPlayer playback, 0.5x–2x speed, ±15 s skip; the progress bar shows marker dots (colored by AI status) and interruption points; tapping a dot seeks while preserving the play/pause state.
- **Timetable**: five periods per weekday (default 08:00–09:40 / 10:00–11:40 / 14:30–16:00 / 16:10–17:40 / 19:00–22:00); both period times and course names are customizable per day; recordings are named by the period matched at start time — "Unnamed" when missing/crossing periods/empty; the actual file name is `{course}_{yyyyMMdd_HHmmss}.m4a`, renaming syncs the file name while keeping the timestamp.
- **Home page**: grouped by day (Today/Yesterday/date), newest first; long-press supports rename, delete to recycle bin, delete audio only, and exporting a single recording.
- **Backup**: exports a zip (manifest.json + database/app.db + recordings/ + images/ + settings.json, including the API key unencrypted); import merges with SHA-256 verification — identical files are skipped, different ones are added with a `_导入_N` suffix.
- **Recycle bin**: restore / delete forever / empty, manual cleanup only.
- **In-app updates**: checks/downloads/installs APKs from this repository's GitHub Releases.

## Build

- Open the root directory in Android Studio (or run `./gradlew assembleRelease`).
- Minimum Android 10 (API 29), target API 35.
- Signing: `app/record.keystore` (kept in the repo for personal builds; password in `app/build.gradle.kts`).

## Releasing a new version (in-app updates)

1. Update `CURRENT_VERSION` in `update/UpdateChecker.kt` (keep it in sync with `versionName`).
2. Commit and tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. GitHub Actions (`.github/workflows/release.yml`) builds the release APK, creates the Release and uploads the APK.
4. Check for updates in the app under "Settings → App update".

## Data & privacy notes

- All data (recordings, photos, AI replies) is stored only in the app's private directory — no accounts, no cloud sync; uninstalling the app deletes everything, so export backups regularly.
- Exported backup zips contain the API key in plain text — keep them safe.
- Only marker photos and the prompt are sent to the AI service you configure; text notes are never uploaded.
