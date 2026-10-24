# Farhang Logat — Kotlin and Jetpack Compose

An offline Persian dictionary, migrated from Java and the Android Support Library to
Kotlin, Jetpack Compose Material 3, and AndroidX.

- Android 17: compile/target SDK 37; Android 6.0+ supported (min SDK 23).
- Android Gradle Plugin 9.1.1, Gradle 9.3.1, Java 17, built-in Kotlin 2.2.10,
  matching Kotlin Compose compiler plugin, Compose BOM 2026.03.00.
- Persian RTL interface, system dark mode, edge-to-edge layout, and resizable windows.
- Offline search and paged browsing of the original 36,098 dictionary entries.
- Word details, selectable definitions, sharing, favorites, recent history,
  supplementary dictionary, bookmarks, and adjustable reading size.
- Database work runs on a dedicated background executor. ViewModel state survives
  rotation; SavedStateHandle restores the query, collection, and detail after recreation.

## Build

Install a compatible Android Studio, Java 17, SDK Platform 37, and Android SDK
Build Tools 36.0.0 through SDK Manager. Set `sdk.dir` in your own `local.properties`
(or configure `ANDROID_HOME`). Local SDK configuration is excluded from Git.

```sh
./gradlew :DictionaryPro:assembleDebug :DictionaryPro:lintDebug
./gradlew :DictionaryPro:connectedDebugAndroidTest
```

The second command requires a connected Android device or emulator. Repository tests
use an isolated database directory and exercise search, pagination, saved-data
persistence, and migration from the original version 2 favorites schema.

The debug APK is generated at
`DictionaryPro/build/outputs/apk/debug/DictionaryPro-debug.apk`.

## Updating existing installations

The application ID remains `ir.iliya.farhanglogat`; version code increases from 5 to 6.
Existing `AppData.db` favorites, history, and bookmarks are preserved. Older favorites
stored only by word are resolved to dictionary IDs without dropping the table.
Bundled dictionary archives are unchanged, with stable entry IDs. Fresh read-only
copies use separate filenames so existing dictionary files are not overwritten.

To publish an update, sign it with the **original release key**. The optional ignored
`release.properties` file supports the existing `keyStore`, `keyStorePassword`,
`keyAlias`, and `keyAliasPassword` properties. Without it, release output is unsigned.

Obsolete Adad/Google Ads integrations and their network/location permissions have
been removed. Pronunciation audio is unavailable, so the UI omits the audio button.
The old embedded web lookup is omitted;
definitions can be shared with other apps. Font-family selection and the old tablet
split-pane setting are replaced by system typography and a resizable single-pane UI.
The archived Java sources, XML layouts, old build configuration, advertising JAR,
audio placeholder, IDE module files, and old release APK have been removed.

## Validation status

Both original SQLite archives pass `PRAGMA integrity_check`; dictionary counts and
plain-text definition formats were checked locally. The debug APK builds successfully,
its signature verifies, and the app launches in an Android 17 ARM64 emulator without
reported crashes. Android lint and the instrumented test suite have not yet run.

Android SDK configuration reference:
https://developer.android.com/about/versions/17/setup-sdk
