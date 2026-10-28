# Release & Deployment Guide

WLED-App uses GitHub Actions (`.github/workflows/release.yml`) and Fastlane (`fastlane/Fastfile`) to automate builds, code signing, and deployments across:

1. **Google Play Store** (`beta` Open Testing track & `production` track)
2. **Apple App Store Connect** (External TestFlight `"Beta"` group & App Store Review submission)
3. **GitHub Releases & IzzyOnDroid (F-Droid)** (Signed, 100% FOSS universal `.apk` + `.sha256` checksum attached to every GitHub Release)

---

## 1. How It Works

### Version Numbers & Build Codes
- **Version Name (`versionName` / `MARKETING_VERSION`)**:
  - Derived automatically from the GitHub Release tag (e.g., `v8.1.0-beta.1` sets Android `versionName = 8.1.0-beta.1` and iOS `MARKETING_VERSION = 8.1.0`, since App Store Connect requires numeric `X.Y.Z` version strings).
- **Build Code (`versionCode` / `CURRENT_PROJECT_VERSION`)**:
  - Computed automatically in CI as `100 + github.run_number` so every Beta and Production upload has a strictly increasing integer without needing manual bumps for each test build (`app-versionCode` in `gradle/libs.versions.toml` and `CURRENT_PROJECT_VERSION` in `project.pbxproj` are only used for local builds).
  - *Note:* If a workflow run partially fails (e.g., Android succeeds but iOS fails), use **Re-run failed jobs** rather than **Re-run all jobs** so the succeeded store isn't re-sent the same build number.

### IzzyOnDroid / F-Droid Compliance
- The codebase contains zero proprietary Google Play Services or Firebase SDKs.
- `app/build.gradle.kts` sets `dependenciesInfo.includeInApk = false`, stripping Google's encrypted dependency metadata block from the release `.apk` while keeping `.aab` metadata intact for Google Play Console SDK warnings.
- Every release attaches `WLED-Native-v<version>.apk` and `WLED-Native-v<version>.apk.sha256` to the GitHub Release after the Play Store upload succeeds, which IzzyOnDroid automatically polls and publishes.

---

## 2. Publishing a Beta / TestFlight Build (from `dev`)

You can send a Beta build to Google Play Open Testing (`beta`) and the TestFlight `"Beta"` external group in any of three ways:

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

1. **Prepare the Changelogs on `dev`**:
   - Rename `app/src/main/assets/changelog/dev.md` → `<version>.md` (e.g. `8.1.0.md`).
   - Rename `iosApp/wled/changelog/dev.md` → `<version>.md` (e.g. `8.1.0.md`).
   - Update `app-versionName` in `gradle/libs.versions.toml` and `MARKETING_VERSION` in `iosApp/wled.xcodeproj/project.pbxproj`.
   - *(Safety net)*: The `Check` workflow on PRs targeting `main` will block merging if `dev.md` hasn't been renamed yet—and if you ever publish a release tag directly without renaming `dev.md`, `release.yml` automatically promotes `dev.md` → `<version>.md` inside the build workspace so your tagged release still succeeds without needing a version bump.
2. **Merge `dev` into `main`**:
   - Open and merge a PR from `dev` → `main`.
3. **Publish the GitHub Release**:
   - Go to **GitHub Releases**, open the draft prepared by Release Drafter, verify the tag (`v8.1.0`) targets `main` and **Set as a pre-release** is unchecked, and click **Publish release**.
   - `.github/workflows/release.yml` will automatically:
     - Upload the signed `.aab` + release notes to the Google Play `production` track.
     - Attach `WLED-Native-v8.1.0.apk` + `.sha256` to the GitHub Release (for **IzzyOnDroid**).
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
