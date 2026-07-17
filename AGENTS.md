# Agent Instructions

## Project Overview

Findroid is a third-party Android Jellyfin client with two app variants: `app:phone` and `app:tv`. The codebase is a multi-module Gradle/Kotlin project using Jetpack Compose and Hilt.

## Build Commands

```bash
# Build debug APK (default flavor: libre)
./gradlew assembleDebug

# Build specific app variant
./gradlew :app:phone:assembleLibreDebug
./gradlew :app:tv:assembleLibreDebug

# Clean build
./gradlew clean assembleDebug
```

## Linting

```bash
# Check formatting (runs on CI for all .kt/.kts changes)
./gradlew ktfmtCheck

# Auto-fix formatting
./gradlew ktfmtFormat
```

ktfmt is applied to all subprojects with `kotlinLangStyle()`.

## Requirements

- **JDK 21** (Temurin distribution in CI)
- Android SDK with API 36 (compileSdk)
- Ruby + Bundler for Fastlane (publishing only)

## Module Structure

| Module | Purpose |
|--------|---------|
| `app:phone` | Phone app entry point |
| `app:tv` | Android TV app entry point |
| `core` | ViewModels, DI, WorkManager workers, utils |
| `data` | Room database, Jellyfin API integration |
| `player:core` | Player interfaces |
| `player:local` | ExoPlayer and libmpv implementation |
| `setup` | Server connection/login UI |
| `modes:film` | Film library UI |
| `settings` | Settings screens |

## Build Variants

- **Build types**: `debug`, `release`, `staging`
- **Product flavors**: `libre` (default)
- Debug adds `.debug` applicationIdSuffix; staging adds `.staging`

## APK Output Paths

Debug APKs are split by ABI:
```
app/phone/build/outputs/apk/libre/debug/phone-libre-{abi}-debug.apk
app/tv/build/outputs/apk/libre/debug/tv-libre-{abi}-debug.apk
```
Where `{abi}` is one of: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.

## Key Architecture Notes

- Hilt for DI; ViewModels use `HiltViewModel`
- Room database with schema in `data/schemas/`
- Core library desugaring enabled (`android.desugar_jdk_libs`)
- Release builds use ProGuard with rules keeping `dev.jdtech.jellyfin.**` class names
- libmpv is `compileOnly` - optional player backend

## Publishing

Triggered on version tags (`v*`). Uses Fastlane:
```bash
bundle exec fastlane publish
```
Requires signing keystore and Play API credentials as environment variables.