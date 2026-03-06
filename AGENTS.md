# AGENTS.md

## Cursor Cloud specific instructions

### Project Overview

AndroidAPS (AAPS) is an open-source Android artificial pancreas system for Type 1 diabetes management. It is a single-product monorepo with ~50 Gradle modules producing two APK targets: `app` (phone) and `wear` (Wear OS watch). See `CONTRIBUTING.md` for coding conventions and PR workflow.

### Prerequisites

- **JDK 21** (required by `buildSrc/src/main/kotlin/Versions.kt`)
- **Android SDK** with `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools` installed at `/opt/android-sdk`
- `ANDROID_HOME=/opt/android-sdk` must be set (added to `~/.bashrc`); `local.properties` with `sdk.dir=/opt/android-sdk` must exist in the repo root

### Key Commands

| Task | Command |
|---|---|
| Build debug APK | `./gradlew -PfirebaseDisable assembleFullDebug --no-daemon` |
| Lint (ktlint) | `./gradlew ktlintCheck --no-daemon` |
| Unit tests | `./gradlew -PfirebaseDisable testFullDebugUnitTest --no-daemon` |
| Unit tests with coverage | `./gradlew -Pcoverage -PfirebaseDisable testFullDebugUnitTest --no-daemon` |

### Gotchas

- **Memory**: The repo's `gradle.properties` requests `org.gradle.jvmargs=-Xmx8g` which can OOM-kill the Gradle daemon in constrained environments. A user-level override at `~/.gradle/gradle.properties` caps heap at 4GB and workers at 4. Always use `--no-daemon` for builds to avoid daemon disappearance.
- **`-PfirebaseDisable` flag**: Pass this flag to skip Firebase/Google Services plugin errors when `google-services.json` credentials are not configured for your environment. The repo ships a `google-services.json` but builds may still need this flag.
- **Pre-existing failures**: `ktlintCheck` has a trailing-comma violation in `app/src/benchmark/kotlin/.../LeakCanaryConfig.kt`. One unit test (`DateUtilImplTest.isSameDayGroup`) may fail depending on timezone. These are pre-existing and not caused by environment setup.
- **No emulator**: Instrumentation / Android tests (`connectedFullDebugAndroidTest`) require an Android emulator or device, which is not available in the Cloud VM. Only unit tests can be run.
- **Git required**: The `app/build.gradle.kts` calls `git describe` and `git status` during configuration. Build will fail if git is unavailable.
