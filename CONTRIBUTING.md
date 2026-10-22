# Contributing to WLED-App

Thank you for contributing to the WLED monorepo! This repository contains the native Android application (`app/`), the native iOS application (`iosApp/`), and shared Kotlin Multiplatform business logic (`shared/`).

## Branching Strategy
* **`main`**: (Default) Stable, released version. **Do not push here directly.**
* **`dev`**: Active development branch. All feature branches and PRs must be based on and target `dev`.

## Submitting Changes
1. Fork the repository.
2. Create your feature branch off of `dev`:
   ```bash
   git checkout -b my-feature dev
   ```
3. **IMPORTANT:** When opening a Pull Request, you must change the **base branch** from `main` to **`dev`**.
   *(GitHub defaults to `main`, so please double-check this!)*

## Code Quality & Linting

### Android & Shared Module (Spotless & Detekt)
The Android and shared Kotlin Multiplatform codebases enforce code style with **Spotless** (ktlint) and static analysis with **Detekt**.

Before committing or submitting a Pull Request, verify and format your code:

```bash
# Automatically format code using Spotless
./gradlew spotlessApply

# Run Spotless verification check
./gradlew spotlessCheck

# Run Detekt static analysis
./gradlew detekt

# Run unit tests
./gradlew test
```

You can optionally install the git pre-commit hook to automate these checks:
```bash
./gradlew installGitHooks
```

### iOS (SwiftLint)
The iOS project uses **SwiftLint** to enforce Swift style and conventions. Before submitting a Pull Request, please ensure that your code passes all lint checks.

#### Installing SwiftLint Locally
To easily catch linting errors during development, we highly recommend installing SwiftLint:

**Via Homebrew:**
```bash
brew install swiftlint
```

#### Running SwiftLint
You can run SwiftLint from the root of the repository or from the `iosApp` directory:

```bash
# From repository root
swiftlint lint --config iosApp/.swiftlint.yml --strict iosApp

# Or within the iosApp directory
cd iosApp && swiftlint lint --strict
```

The `iosApp/.swiftlint.yml` configuration file defines the active rules. By default, the CI pipeline will block Pull Requests that contain SwiftLint errors.
 
#### Running iOS Unit Tests
To run iOS unit tests from the command line:

```bash
xcodebuild test \
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

For more details on building the iOS application and assembling shared Kotlin Multiplatform frameworks, see [iosApp/README.md](iosApp/README.md).

## Pull Request Labels
To ensure release notes are generated correctly, please add appropriate labels to your Pull Request. The automation relies on these labels to categorize changes and determine the version number.

- **For categorization:** `feature`, `enhancement`, `bug`, `fix`, `documentation`, `chore`, `refactor`.
- **For versioning:** `major` (for breaking changes), `minor` (for features), `patch` (for fixes).

## Hotfixes
If you are fixing a critical bug in production:
1. Branch off `main`.
2. Submit a PR to `main`.
3. **Important:** You must also merge these changes back into `dev` to ensure the bug doesn't reappear in the next release.

## Updating the Changelog
When your Pull Request introduces a new user-facing feature or an important bug fix, please update the development changelog file(s):
- **Android:** `app/src/main/assets/changelog/dev.md`
- **iOS:** `iosApp/wled/changelog/dev.md`

When a feature or fix applies to both platforms, remember to update **both** `dev.md` files! **Do not** create new versioned files (like `7.2.0.md`) or edit production changelogs for new PRs. Keeping `dev.md` up to date ensures all new features are correctly aggregated for the next release and displayed clearly for beta testers!

### Changelog Style Guidelines
- **Section Headings over Bolded Bullet Lists:** Prefer `###` section titles with short, playful descriptions over bullet lists that start with bold words (e.g., avoid `- **Feature**: description`). Bullet lists under a `###` title are fine if a major feature needs sub-items.
- **Minor Features & Fixes:** Group minor features, small tweaks, and bug fixes at the bottom under `### Other Improvements` (or `### Other Improvements & Fixes` when bug fixes are included) using plain bullet points.
- **No Emojis:** Do not use emojis in headings or at the beginning of list items.
