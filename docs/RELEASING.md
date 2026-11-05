# Release & Deployment Guide

WLED-App uses GitHub Actions (`.github/workflows/release.yml`) and Fastlane (`fastlane/Fastfile`) to automate builds, code signing, and deployments across:

1. **Google Play Store** (`beta` Open Testing track & `production` track)
2. **Apple App Store Connect** (External TestFlight `"Beta"` group & App Store Review submission)
3. **GitHub Releases & IzzyOnDroid (F-Droid)** (Signed, 100% FOSS universal `.apk` attached to every GitHub Release)

---

## 1. How It Works

### Version Numbers & Build Codes
- **Single Source of Truth (`gradle/libs.versions.toml` & `project.pbxproj`)**:
  - `app-versionName` and `app-versionCode` in `gradle/libs.versions.toml` (and `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `iosApp/wled.xcodeproj/project.pbxproj`) are bumped by `/prepare-release` before every Beta and Production release.
  - Because `release.yml` reads `app-versionName` and `app-versionCode` directly from `gradle/libs.versions.toml` without overriding them via `-P` flags or mutating tracked files, any third party (such as **IzzyOnDroid / F-Droid**) building from the release git tag gets a byte-for-byte **Reproducible Build**.

### IzzyOnDroid / F-Droid Compliance
- The codebase contains zero proprietary Google Play Services or Firebase SDKs.
- `app/build.gradle.kts` sets `dependenciesInfo.includeInApk = false`, stripping Google's encrypted dependency metadata block from the release `.apk` while keeping `.aab` metadata intact for Google Play Console SDK warnings.
- `release.yml` builds `:app:assembleRelease` directly from the committed `gradle/libs.versions.toml` values without build-time `-PversionCode` overrides, ensuring 100% **Reproducible Builds**.
- Every release attaches `WLED-Native-v<version>.apk` to the GitHub Release after the Play Store upload succeeds, which IzzyOnDroid automatically polls and publishes.

---

## 2. Publishing a Beta / TestFlight Build (from `dev`)

*(Tip: Run the `/prepare-release` skill to automatically audit commits/PRs since the last stable release, verify `dev.md` on both platforms, bump `app-versionCode` and `app-versionName` in a release-prep PR to `dev`, and prepare the incremental Beta GitHub Release draft.)*

Once the Beta release-prep PR (bumping `app-versionCode` by `+1` and `app-versionName` to e.g. `8.1.0-beta.1`) is merged into `dev`, you can trigger the Beta build to Google Play Open Testing (`beta`) and the TestFlight `"Beta"` external group in any of three ways:

### Option A: Publish the Release Drafter Draft as a Pre-Release (GitHub UI)
1. Go to **GitHub Releases** and open the draft created by Release Drafter.
2. Set the tag to e.g. `v8.1.0-beta.1`, target branch `dev`, check **Set as a pre-release**, and click **Publish release**.

### Option B: Create a Pre-Release from the Terminal (Fastest CLI)
```bash
gh release create v8.1.0-beta.1 --prerelease --target dev --title "v8.1.0-beta.1" --generate-notes
```

### Option C: Manual Workflow Dispatch (or Dry Run)
1. Go to **Actions → Release → Run workflow**.
2. Select branch `dev`, track `beta`, and enter `version_name` (e.g. `8.1.0-beta.1`).
3. *(Optional)* Check **dry_run** to test building, code signing, Google Play validation (`validate_only: true`), and iOS IPA export end-to-end without publishing to stores or creating a GitHub Release.

---

## 3. Publishing a Production Release (from `main`)

*(Tip: Run the `/prepare-release` skill to automate Step 1—auditing changes, rewriting `<version>.md` for end users, translating via `/translate-changelog`, bumping version numbers, opening the PR to `dev`, and preparing the GitHub Release draft with a human-friendly summary.)*

1. **Prepare the Changelogs on `dev`**:
   - Rename `app/src/main/assets/changelog/dev.md` → `<version>.md` (e.g. `8.1.0.md`).
   - Rename `iosApp/wled/changelog/dev.md` → `<version>.md` (e.g. `8.1.0.md`).
   - Create `fastlane/changelogs/<version>.txt` and curate the top highlights so it stays within Google Play's **500-character limit** (`fastlane/changelogs/README.md`), while keeping `app/src/main/assets/changelog/<version>.md` full-length for the in-app "What's New" sheet.
   - Run the `/translate-changelog` skill to generate localized `<version>.<lang>.md` release notes (e.g. `8.1.0.fr.md`, `8.1.0.de.md`, `8.1.0.zh.md`) and localized `fastlane/changelogs/<version>.<lang>.txt` summaries (`<= 500` chars each). Both the in-app "What's New" sheet and Fastlane store uploads automatically pick up localized files when present and fall back to English.
   - Increment `app-versionCode` by `+1` and update `app-versionName` in `gradle/libs.versions.toml`, and update `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `iosApp/wled.xcodeproj/project.pbxproj`.
   - *(Safety net)*: The `Check` workflow on PRs targeting `main` will block merging if `dev.md` hasn't been renamed, if `fastlane/changelogs/<version>.txt` is missing, or if `app-versionName` still has a `-beta` suffix, and `release.yml` verifies in `prepare` that the tag matches `gradle/libs.versions.toml`.
2. **Merge `dev` into `main`**:
   - Open and merge a PR from `dev` → `main`.
3. **Publish the GitHub Release**:
   - Go to **GitHub Releases**, open the draft prepared by Release Drafter, verify the tag (`v8.1.0`) targets `main` and **Set as a pre-release** is unchecked, and click **Publish release**.
   - `.github/workflows/release.yml` will automatically:
     - Upload the signed `.aab` + release notes to the Google Play `production` track.
     - Attach `WLED-Native-v8.1.0.apk` to the GitHub Release (for **IzzyOnDroid**).
     - Upload the signed `.ipa` + release notes to App Store Connect and submit for App Store Review.

---

## 4. One-Time GitHub Secrets Setup

Add the following secrets in **Settings → Secrets and variables → Actions → Repository secrets**:

### Android & Google Play Secrets

| Secret Name | Description |
| :--- | :--- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload keystore (`.jks` / `.keystore`). Generate with: `base64 -i release.keystore \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Password for the Android keystore. |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore. |
| `ANDROID_KEY_PASSWORD` | Password for the key alias. |
| `GOOGLE_PLAY_JSON_KEY` | Full JSON contents of your Google Cloud Service Account key linked to Google Play Console (with *Release to production, exclude devices, and use Play App Signing* and *Release apps to testing tracks* permissions). |

### iOS & App Store Connect Secrets

| Secret Name | Description |
| :--- | :--- |
| `IOS_DIST_CERT_BASE64` | Base64-encoded Apple Distribution certificate (`.p12` exported from Keychain Access). Generate with: `base64 -i Certificates.p12 \| pbcopy` |
| `IOS_DIST_CERT_PASSWORD` | Password used when exporting the `.p12` certificate. |
| `ASC_KEY_ID` | App Store Connect API Key ID (e.g. `2X9R4HXF34` from **App Store Connect → Users and Access → Integrations → App Store Connect API** with **App Manager** or **Admin** role). |
| `ASC_ISSUER_ID` | App Store Connect API Issuer UUID (shown at the top of the Integrations page). |
| `ASC_KEY_P8_BASE64` | Base64-encoded contents of the downloaded `AuthKey_<KEY_ID>.p8` file. Generate with: `base64 -i AuthKey_XXXXXXXXXX.p8 \| pbcopy` |

*(Optional Repository Variable)*:
- `IOS_TESTFLIGHT_GROUP`: Defaults to `Beta`. Set this under **Variables** if you ever rename your external TestFlight group in App Store Connect.

> **Note on Xcode Cloud:** If you previously had an Xcode Cloud workflow configured in **App Store Connect → Xcode Cloud**, disable or delete it there so Xcode Cloud does not attempt duplicate builds alongside GitHub Actions.
