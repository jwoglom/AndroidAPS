# Plan: Improve CI Build Speed & Iteration Time for AndroidAPS

## Current State Analysis

### CircleCI (master/dev) — ~11 min build, ~2 hr queue wait
| Step | Duration |
|------|----------|
| compileFullDebugAndroidTestSources | 2m 17s |
| connectedFullDebugAndroidTest | 2m 48s |
| testFullDebugUnitTest | **5m 26s** (biggest) |
| jacocoAllDebugReport | 9s |
| Queue wait (before build starts) | **~113 min** |

### GitHub Actions (PRs) — manual workflow_dispatch only
- Builds APK + uploads to Google Drive
- No automated test step on PRs
- Uses `actions/setup-java` with `cache: gradle` but log shows "gradle cache is not found"
- Installs Android SDK from scratch every run

### Project Structure
- **49 modules** across 7 groups
- **4 product flavors** × 2 build types = 8 variants per module
- Gradle 9.0.0, JDK 21, AGP 8.13.2, Kotlin 2.2.21
- Configuration cache **disabled** (commented out — "causing issues with CircleCI")

---

## Optimization Plan

### 1. Enable Gradle Build Cache (High Impact, Low Effort)

**File: `gradle.properties`**

Currently there is NO build cache enabled. The configuration cache is commented out, but the regular **build cache** (different from configuration cache) is not mentioned at all.

- Add `org.gradle.caching=true` to `gradle.properties`
- The GH Actions workflows already pass `-Dorg.gradle.caching=true` on the command line, but this should be in `gradle.properties` so it applies everywhere (local builds, CircleCI)
- For CI, configure a remote build cache (Gradle's built-in HTTP cache server or Develocity) so builds across CI runs can share cached outputs
- For local dev, the local build cache (enabled by default with `org.gradle.caching=true`) will speed up rebuilds after branch switches

**Expected impact:** 30-50% faster incremental builds across all environments.

### 2. Re-enable Configuration Cache (High Impact, Medium Effort)

**File: `gradle.properties`**

The comment says it "causes issues with CircleCI and maybe Studio 2021". Given the project now uses Gradle 9.0.0, AGP 8.13.2, and modern tooling, configuration cache compatibility has improved dramatically.

- Uncomment/add `org.gradle.configuration-cache=true`
- Run a full build to identify any remaining incompatibilities
- Fix any plugin issues (common culprits: older KSP versions, custom tasks accessing `Project` at execution time)
- If CircleCI specifically breaks, conditionally disable it only there via CI env var override

**Expected impact:** 10-20% faster builds by skipping configuration phase on re-runs.

### 3. Reduce Product Flavor Overhead (High Impact, Medium Effort)

**File: `buildSrc/src/main/kotlin/android-module-dependencies.gradle.kts` and all module build files**

Currently **all 49 modules** define 4 product flavors (full, pumpcontrol, aapsclient, aapsclient2), but most library modules don't have flavor-specific code. This quadruples compile tasks.

- Add `missingDimensionStrategy` to library modules or use `flavorCompat` to avoid unnecessary variant multiplication
- Better: use **variant filtering** to limit which variants get built in library modules that don't need all flavors:
  ```kotlin
  androidComponents {
      beforeVariants { variant ->
          if (variant.flavorName != "full" && variant.buildType == "debug") {
              variant.enable = false
          }
      }
  }
  ```
- Even if we only filter out pumpcontrol/aapsclient/aapsclient2 debug variants from leaf library modules, it eliminates a huge number of compile tasks

**Expected impact:** 20-40% faster clean builds by eliminating unnecessary variant compilation.

### 4. Fix Gradle Dependency Caching in GitHub Actions (Medium Impact, Low Effort)

**Files: `.github/workflows/aaps-ci.yml`, `pr-ci.yml`, `cherry-pick-ci.yml`**

The GH Actions log shows "gradle cache is not found". The `actions/setup-java@v4` with `cache: gradle` should handle this, but the cache key may not be matching.

- Add explicit `actions/cache@v4` step for `~/.gradle/caches` and `~/.gradle/wrapper` with a key based on `hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', 'gradle/libs.versions.toml')`
- Also cache `~/.android/build-cache` for Android build cache artifacts
- Consider using `gradle/actions/setup-gradle@v4` instead of raw java setup — it provides better Gradle caching out of the box

**Expected impact:** 1-3 minutes saved per GH Actions run from avoiding dependency re-download.

### 5. Add Automated PR Test Workflow (Medium Impact, Medium Effort)

**File: new `.github/workflows/pr-tests.yml`**

Currently PRs have NO automated testing — only manual `workflow_dispatch` builds. This is a major gap.

- Create a workflow triggered on `pull_request` events that runs unit tests:
  ```yaml
  on:
    pull_request:
      branches: [master, dev]
  ```
- Run `testFullDebugUnitTest` (the biggest test suite) as a mandatory check
- Use proper caching (from item 4)
- Consider running only tests for **changed modules** using a path-based filter or gradle's `--changed-since` approach

**Expected impact:** Catches bugs earlier; with caching, should complete in ~5-7 min on GH Actions.

### 6. Parallelize CircleCI Steps (High Impact, Low Effort)

**File: `.circleci/config.yml`**

Currently, steps run sequentially: compile → instrumented tests → unit tests → coverage. The emulator is launched in background, which is good, but unit tests wait for instrumented tests to finish.

- **Split into parallel jobs**: Run `testFullDebugUnitTest` and `connectedFullDebugAndroidTest` as separate parallel CircleCI jobs
- Use CircleCI workspaces to share the compiled outputs from a compile job
- Structure:
  ```
  compile-job → (workspace persist)
    ├── unit-test-job (attach workspace, run unit tests)
    ├── instrumented-test-job (attach workspace, run connected tests)
    └── coverage-job (attach workspace, depends on both test jobs, merge coverage)
  ```

**Expected impact:** ~3 minutes saved (unit tests and instrumented tests overlap instead of running sequentially). Total wall time drops from ~11 min to ~8 min.

### 7. Optimize CircleCI Queue Time (Very High Impact, Infrastructure)

The data shows **~113 minutes** between `queued_at` and `start_time`. This is because CircleCI uses a self-hosted runner (`nightscout/android` resource class) with limited capacity.

- **Option A:** Add more self-hosted runners to the pool
- **Option B:** Migrate the CircleCI workload to GitHub Actions (which already has the workflows and uses `ubuntu-latest` — no queue wait)
- **Option C:** Use CircleCI's cloud resource classes instead of self-hosted for faster availability

**Expected impact:** Eliminates 90+ minute queue wait.

### 8. Optimize Gradle JVM and Worker Settings (Low-Medium Impact, Low Effort)

**File: `gradle.properties`**

Current settings have some inconsistencies:
- `gradle.properties`: `org.gradle.workers.max=12`
- CircleCI overrides to `22` for unit tests
- GH Actions overrides to `8`
- Stack size `-Xss1024m` (1GB per thread!) is extremely high — typical is 1-4MB

Changes:
- Reduce `-Xss` from `1024m` to `4m` — this wastes enormous memory per thread
- Let `workers.max` auto-detect from CPU count instead of hardcoding (remove the property, or set per-environment)
- Add `-XX:+HeapDumpOnOutOfMemoryError` for debugging
- Consider adding `-XX:MaxMetaspaceSize=512m` to bound metaspace

**Expected impact:** More stable builds, less GC pressure, potentially faster compilation.

### 9. Enable Kotlin Incremental Compilation Improvements (Medium Impact, Low Effort)

**File: `gradle.properties`**

- Add `kotlin.incremental=true` (should be default but worth being explicit)
- Add `kotlin.incremental.useClasspathSnapshot=true` for better incremental compilation with classpath changes
- The current `kotlin.compiler.execution.strategy="in-process"` in CI is good for CI (avoids daemon startup) but for local dev, the default daemon mode is better — remove the CI override from `gradle.properties` if present and keep it only in CI scripts

**Expected impact:** 10-30% faster incremental Kotlin compilation for local dev.

### 10. Use `--no-daemon` in CI and Remove Redundant Daemon Flag (Low Impact, Low Effort)

**File: `.circleci/config.yml`, GH Actions workflows**

CI builds are ephemeral — the Gradle daemon provides no benefit since there's no warm daemon to reuse. Using `--no-daemon` avoids the overhead of daemon startup and communication.

However, with `kotlin.compiler.execution.strategy="in-process"`, the daemon is being used as the JVM for compilation. The better approach:
- Keep the daemon for CI (since in-process compilation needs it)
- But ensure daemon JVM args match the build JVM args to avoid a daemon restart

**Expected impact:** Minimal, but cleaner configuration.

### 11. Optimize Android SDK Setup in GitHub Actions (Low-Medium Impact, Low Effort)

**Files: GH Actions workflows**

The GH Actions log shows SDK setup and license acceptance taking time.

- Pin the Android SDK components needed and cache `$ANDROID_HOME` or use `android-actions/setup-android@v3` with caching
- Pre-accept licenses in a single step
- Use `actions/cache` for the Android SDK directory

**Expected impact:** 30-60s saved per GH Actions run.

### 12. Consider Module-Level Test Sharding (Medium Impact, Higher Effort)

With 49 modules and tests spread across them, CircleCI's `testFullDebugUnitTest` runs all module tests sequentially in a single Gradle invocation.

- Use Gradle's `--parallel` (already enabled) but also consider `maxParallelForks` tuning per module
- Current setting: `maxParallelForks = Runtime.getRuntime().availableProcessors() / 2` — this is per-module; combined with Gradle parallelism, this could over-subscribe CPUs
- For CI with known CPU counts, set this explicitly

---

## Priority Order (by impact/effort ratio)

| Priority | Item | Impact | Effort |
|----------|------|--------|--------|
| 1 | Enable Gradle Build Cache (#1) | High | Low |
| 2 | Fix GH Actions Caching (#4) | Medium | Low |
| 3 | Optimize JVM settings — fix Xss (#8) | Medium | Low |
| 4 | Parallelize CircleCI jobs (#6) | High | Low-Med |
| 5 | Reduce flavor variants (#3) | High | Medium |
| 6 | Re-enable Configuration Cache (#2) | High | Medium |
| 7 | Add PR test workflow (#5) | Medium | Medium |
| 8 | Address queue time (#7) | Very High | Infra decision |
| 9 | Kotlin incremental improvements (#9) | Medium | Low |
| 10 | SDK caching (#11) | Low-Med | Low |

## Implementation Approach

I'll implement items 1-4 and 6-9 as concrete code changes. Items 5 and 7 require more invasive changes and should be discussed. Item 7 (queue time) is an infrastructure decision.

The changes will touch:
- `gradle.properties` — build cache, JVM settings, Kotlin settings
- `.circleci/config.yml` — parallel jobs, workspace sharing
- `.github/workflows/*.yml` — improved caching, optional test workflow
- `buildSrc/src/main/kotlin/android-module-dependencies.gradle.kts` — variant filtering (if approved)
