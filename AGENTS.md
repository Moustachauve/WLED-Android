# WLED-App - Agent Instructions

## 1. Project Overview
WLED-App is the monorepo for the official native **WLED** application on Android and iOS for discovering and controlling [WLED](https://github.com/wled/WLED) devices (WiFi-controlled LED strips). It features automatic mDNS discovery, a unified device list, WebSocket state synchronization, OTA firmware updates, and light/dark themes.

*   **Upstream WLED Reference:** Always reference `https://github.com/wled/WLED` (never the legacy `Aircoookie/WLED` repository) and use realistic firmware versions (`0.14.x`, `0.15.x`) in tests and fixtures.

## 2. Tech Stack & Architecture
*   **Languages:** Kotlin (Multiplatform & Android), Swift 6 (iOS)
*   **UI:** Jetpack Compose + Material 3 + Jetpack Glance Widgets (Android, Min SDK 24 / Target SDK 37); SwiftUI (iOS 18.0+)
*   **Architecture:** Clean Architecture / MVVM with shared business logic in `:shared` (`commonMain`, `androidMain`, `iosMain`) consumed by `:app` and `iosApp/` (`SharedPackage` XCFramework + SKIE)
*   **Dependency Injection:** Koin (`SharedModule.kt`, `AppModule.kt`, `KoinIos.kt`). Whenever modifying DI bindings, update and run `KoinModuleTest`.
*   **Asynchronous & Concurrency:** Kotlin Coroutines & Flow (`StateFlow`, `SharedFlow`), bridged to Swift `AsyncSequence` via SKIE.
    *   *Dispatcher Injection:* Inject `CoroutineDispatcher` and `CoroutineScope` into services/managers rather than hardcoding `Dispatchers.IO` or `Dispatchers.Default` so unit tests run deterministically with `runTest`.
    *   *State Consolidation:* Consolidate per-device runtime state (jobs, WebSocket handles, metadata) into a single state-holder data class in one map keyed by MAC address rather than maintaining multiple parallel `MutableMap` properties.
    *   *Lifecycle Cleanup:* Track or bind all coroutines launched in `init`/`start()` to a scope cancelled in `destroy()`/`close()`, and always rethrow `CancellationException` in catch blocks.
*   **Network:** Ktor Client (`OkHttp` engine on Android, `Darwin` engine on iOS), Ktor WebSockets, `kotlinx.serialization` (JSON)
*   **Database:** Room KMP in `:shared` (exports schemas to `app/schemas/`). When modifying an `@Entity`, increment the schema version and provide a migration + migration test.
*   **DataStore:** DataStore Core + Okio + `kotlinx.serialization` (`UserPreferences.kt` in `:shared`, with migration from legacy `app/src/main/proto/user_prefs.proto`). Do not use `SharedPreferences`.
*   **Testing by Source Set:**
    *   `app/src/test/`: JUnit 5 (JUnit Jupiter), MockK, **Selfie** snapshot testing (`expectSelfie(...)`).
    *   `shared/src/commonTest/` & `shared/src/iosTest/`: `kotlin.test`, `kotlinx-coroutines-test`, `ktor-client-mock` (`MockEngine`). *(Note: MockK and Selfie are JVM-only and unavailable in `commonTest`.)*
    *   `shared/src/androidHostTest/`: `kotlin.test` + MockK for Android host tests of `:shared`.
    *   `iosApp/wledTests/`: Swift Testing / XCTest.
    *   *Single Behavior:* Each unit test must verify **one** behavior, state transition, or scenario. Never combine sequential state transitions into a monolithic test method.
    *   *Ergonomics, DRY & No Test Theater:* Extract concise helper functions, fixtures, or parameterized loops (`forEach`) to avoid boilerplate. Do not write test theater (testing stdlib behavior, mock return values, or continuous stress/timing loops).
    *   *Selfie Snapshots (`:app` tests):* Use `expectSelfie(...)` instead of repetitive `assertEquals` blocks for models, serialization, DTOs, migrations, or complex state:
        *   Use `.toMatchDisk()` for multiline JSON, DTOs, or complex objects (commit co-located `.ss` files).
        *   Use `.toBe(...)` for short, single-line values.
        *   Use `.toBe_TODO()` or `.toMatchDisk_TODO()` when creating new tests; running `./gradlew :app:testDebugUnitTest` records and populates snapshots automatically.

## 3. Code Style, Linting & Verification
*   **Spotless (ktlint) & Detekt:** Enforce Kotlin formatting and static analysis across `:app` and `:shared`.
*   **SwiftLint:** Enforces Swift style in `iosApp/` (`iosApp/.swiftlint.yml`).
*   **No Suppressions or Baseline Growth:** Resolve Detekt, ktlint, and SwiftLint findings by fixing the underlying code. Only use `@Suppress` when strictly necessary (e.g., `TooGenericExceptionCaught` at network/serialization boundaries) and always include an inline comment explaining why. **Never** add entries to `app/detekt-baseline.xml` (only remove them).
*   **Localization:** The app is localized (`en`, `fr`, `zh`). Never hardcode UI strings; extract Android strings to `app/src/main/res/values/strings.xml` and iOS strings to `iosApp/wled/Localizable.xcstrings`.

**Verification Commands:**
```bash
# 1. Format & Static Analysis (Kotlin + Swift)
./gradlew spotlessApply detekt
swiftlint lint --config iosApp/.swiftlint.yml --strict iosApp

# 2. Fast Unit Tests (Android & Shared)
./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test

# 3. iOS App Build & Tests (Assemble Debug XCFramework first; SKIP_KMP_BUILD=1 avoids duplicate Gradle build inside Xcode)
./gradlew :shared:assembleSharedDebugXCFramework
SKIP_KMP_BUILD=1 xcodebuild test \
  -project iosApp/wled.xcodeproj \
  -scheme wled \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=latest' \
  -skipPackagePluginValidation \
  -skipMacroValidation \
  -parallel-testing-enabled NO \
  -maximum-concurrent-test-simulator-destinations 1 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO
```
*   **Pre-Commit Hook:** A Git pre-commit hook (`.git/hooks/pre-commit`, installed via `./gradlew installGitHooks`) blocks commits if `spotlessCheck`, `detekt`, or `swiftlint` fail.
*   **Clean Working Tree:** Before committing, check `git status` to ensure IDE or Xcode build side-effects (e.g., unintended timestamps in `iosApp/wled/Localizable.xcstrings` or `.idea/` files) are not left dirty or accidentally staged.

## 4. Git & Pull Request Rules
*   **Branches:** `main` is the stable release branch (never push or target PRs directly to `main` except hotfixes, which must be merged back into `dev`). Base all feature branches and PRs on `dev`.
*   **Focused PRs:** Keep PRs small and single-purpose. Split large multi-step migrations into stacked PRs rather than creating 1,000+ line diffs, and do not bundle unrelated drive-by changes.
*   **PR Labels:** Apply both a categorization label (`feature`, `enhancement`, `bug`, `fix`, `documentation`, `chore`, `refactor`) and a versioning label (`major`, `minor`, `patch`).
*   **CI Checks:** Do not poll `gh pr checks` in a tight loop; use `gh pr checks --watch` or wait for user feedback.

## 5. Project Structure & Navigation Pointers
*   `shared/src/commonMain/kotlin/ca/cgagnier/wlednativeandroid/` - Shared KMP code:
    *   `di/` - Shared Koin module (`SharedModule.kt`).
    *   `model/` - Entities (`Device`, `VersionWithAssets`), domain state (`DeviceWithState`), and API DTOs (`model/wledapi/`, `model/githubapi/`).
    *   `repository/` - `DeviceRepository`, `VersionWithAssetsRepository`, `UserPreferencesRepository`, Room `DevicesDatabase` & migrations (`repository/migrations/`).
    *   `service/` - `DeviceConnectionManager`, `DeviceFirstContactService`, `NetworkConnectivityManager`, `DeviceDiscovery`, WebSockets (`service/websocket/`), HTTP APIs (`service/api/`), and OTA/release services (`service/update/`).
    *   `util/` - Multiplatform utilities and extensions.
*   `shared/src/androidMain/` & `shared/src/iosMain/` - Platform implementations (`AndroidDeviceDiscovery`, `IosDeviceDiscovery`, platform Ktor engines, Room builders, `KoinIos.kt`).
*   `app/src/main/java/ca/cgagnier/wlednativeandroid/` - Native Android app:
    *   `di/` - Android Koin module (`AppModule.kt`).
    *   `domain/` - Android-specific use cases and `ChangelogProvider`.
    *   `service/` - Android background/platform services (`service/controls/`, `service/update/`).
    *   `ui/` - Jetpack Compose screens, ViewModels, and Navigation 3 routing (`MainNavHost.kt`).
    *   `widget/` - Jetpack Glance app widgets (deep-linking into singleTop `MainActivity.onNewIntent()`).
*   `iosApp/` - Native iOS app ([iosApp/README.md](iosApp/README.md)):
    *   `iosApp/wled/` - SwiftUI views (`View/`), view models (`ViewModel/`), services (`Service/`), and CoreData-to-Room migration (`Migration/`).
    *   `iosApp/wledTests/` - iOS unit tests (note lowercase `wledTests`).
    *   `iosApp/SharedPackage/` - Local Swift Package wrapping `Shared.xcframework`.
    *   `iosApp/scripts/compile_kmp.sh` - Xcode build phase script that builds `:shared` XCFramework.
*   `.agents/skills/` & `.agent/skills/` - Official Android (`android/skills`) and Swift (`twostraws`) agent skills managed via `skills-lock.json`.

**Key Configuration Files:**
*   `gradle/libs.versions.toml` - Version catalog for all dependencies and plugins.
*   `config/detekt/detekt.yml` & `app/detekt-baseline.xml` - Detekt configuration and legacy baseline.
*   `iosApp/.swiftlint.yml` - SwiftLint configuration.
*   `app/src/main/assets/changelog/dev.md` - Development changelog for user-facing changes (see [CONTRIBUTING.md](CONTRIBUTING.md)).
