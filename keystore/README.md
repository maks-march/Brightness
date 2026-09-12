# Signing key

The local development keystore is intentionally used by both `debug` and `release` build types. This is required for in-app APK replacement: Android only accepts an update signed by the same key as the installed app.

Before distributing the app, replace `brightness-release.jks` with a private production keystore and set `SIGNING_STORE_PASSWORD` and `SIGNING_KEY_PASSWORD` outside the repository (for example in `~/.gradle/gradle.properties` or CI secrets).
