# Keep the update JSON model and FileProvider discoverable in release builds.
-keep class org.json.** { *; }
-keep class androidx.core.content.FileProvider { *; }
