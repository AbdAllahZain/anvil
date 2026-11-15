# Anvil

A code editor for Android with a terminal, Git and project-wide search.
Kotlin, Jetpack Compose, and the Sora Editor (TextMate highlighting).

## Build the APK without installing anything

1. Create a GitHub repository and upload everything in this folder
   (keep the `.github/workflows/build.yml` path exactly).
2. Open the **Actions** tab and wait for "Build APK" to finish (5–10 minutes).
3. Open the finished run and download **Anvil-debug-apk** from *Artifacts*.
4. Unzip it and install `app-debug.apk` on your phone.

## Building locally

Open the folder in Android Studio (Ladybug or newer) and run the `app` configuration.
Or, with Gradle 8.11+ and JDK 17: `gradle :app:assembleDebug`.

## What is inside

- Editor: syntax highlighting for 27 languages, tabs, undo/redo, find and replace,
  go to line, sticky scroll, indent guides, auto-closing pairs, pinch to zoom, auto-save.
- Explorer: tree view, create / rename / duplicate / delete, hidden files toggle.
- Search in project with regular expressions.
- Source control (JGit): init, clone, stage, commit, diff, branches, pull, push.
- Terminal (`/system/bin/sh`) and an Output panel for JavaScript console logs.
- Run: JavaScript in a built-in WebView, HTML and Markdown preview.
- Command palette (Ctrl+Shift+P), quick open (Ctrl+P), hardware keyboard shortcuts.
- Dark and light themes, JetBrains Mono for code and Inter for the interface.

## Notes

- Grant "All files access" on the start screen to open folders anywhere on the device.
- Android ships a minimal shell, so compilers and runtimes (Python, Node, gcc) are only
  available if the device already has them.
- Git credentials are stored in the app's private storage.
