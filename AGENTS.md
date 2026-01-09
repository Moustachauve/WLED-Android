# WLED-Android - Agent Instructions

This file serves as the primary instruction set for AI agents working on the WLED-Android codebase.

## 1. Project Overview
WLED-Android is a native Android application for controlling WLED devices (WiFi-controlled LED strips). It features automatic device discovery (mDNS), a unified device list, custom naming, and support for light/dark modes. It replaces the legacy WLED app.

## 2. Tech Stack & Architecture
*   **Language:** Kotlin
*   **UI:** Jetpack Compose, Material 3, Jetpack Glance (Widgets)
*   **Architecture:** Clean Architecture / MVVM (Model-View-ViewModel)
*   **Dependency Injection:** Hilt
*   **Asynchronous:** Coroutines & Flow
*   **Network:** Retrofit, OkHttp, Moshi (JSON), Protobuf
*   **Database:** Room (Exports schemas to `app/schemas`)
    *   *Instruction:* If modifying a generic `@Entity`, consider database migrations and schema versioning.
*   **DataStore:** Protobuf (Managed via `app/src/main/proto/user_prefs.proto`)
    *   *Instruction:* Modify the `.proto` definition for user preferences. Do not use standard SharedPreferences.
*   **Build System:** Gradle (Kotlin DSL), Version Catalogs
*   **Min SDK:** 24
*   **Target SDK:** 36

## 3. Code Style & Quality (CRITICAL)
This project enforces strict code style, static analysis, and localization rules.

*   **Spotless:** Used for code formatting (configured with ktlint).
*   **Detekt:** Used for static code analysis.
*   **Localization:** The app is localized (English `en`, French `fr`, Chinese `zh`).
    *   **Instruction:** **NEVER** hardcode UI strings. Always extract them to `app/src/main/res/values/strings.xml`.

**INSTRUCTIONS FOR AGENTS:**
*   **ALWAYS** run the following command before submitting any code changes to fix formatting issues:
    ```bash
    ./gradlew spotlessApply
    ```
*   **CHECK** for static analysis issues by running:
    ```bash
    ./gradlew detekt
    ```
*   **Note:** Git pre-commit hooks are installed that will **BLOCK** commits if `spotlessCheck` or `detekt` fail. You must ensure these pass before attempting to commit.

## 4. Git & Contribution Rules
*   **Default Branch:** `main` (Stable release). **NEVER** push or open PRs directly to `main`.
*   **Development Branch:** `dev`. All feature branches and PRs must be based on and target `dev`.
*   **Hotfix Strategy:**
    1.  Branch off `main`.
    2.  Submit PR to `main`.
    3.  **IMPORTANT:** Merge changes back into `dev` to prevent regressions.
*   **PR Labels:** You must apply labels to Pull Requests to ensure correct release note generation:
    *   **Categorization:** `feature`, `enhancement`, `bug`, `fix`, `documentation`, `chore`, `refactor`
    *   **Versioning:** `major` (breaking), `minor` (feature), `patch` (fix)

## 5. Project Structure
The code is located in `app/src/main/java/ca/cgagnier/wlednativeandroid/`.

*   `di/` - Hilt dependency injection modules.
*   `domain/` - Domain logic and use cases.
*   `model/` - Data models (Entities, DTOs).
*   `repository/` - Data access layer (Repositories).
*   `service/` - Background services.
*   `ui/` - User Interface (Jetpack Compose screens and components).
*   `util/` - Utility classes.
*   `widget/` - Jetpack Glance app widgets.
