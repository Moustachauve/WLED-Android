# WLED for iOS

<a href='https://apps.apple.com/us/app/wled-native/id6446207239'><img alt='Download on the App Store' src='https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg' height='60'/></a>

Native iOS application for discovering and controlling [WLED](https://github.com/Aircoookie/WLED) devices, built with SwiftUI as part of the [WLED-android](../README.md) monorepo.

## Architecture

- **UI:** SwiftUI (Targeting iOS 16+)
- **Shared Logic:** Consumes the Kotlin Multiplatform shared module (`:shared`) via `SharedPackage` (XCFramework with SKIE)
- **Discovery & Networking:** Bonjour / mDNS network discovery and WebSocket communication

## Building & Testing

### 1. Build Shared KMP Framework
The iOS application depends on the shared Kotlin Multiplatform module (`:shared`). Assemble the XCFramework before building in Xcode:

```bash
# Debug build (local development)
./gradlew :shared:assembleSharedDebugXCFramework

# Release build
./gradlew :shared:assembleSharedReleaseXCFramework
```

### 2. Build iOS App
Open `iosApp/wled.xcodeproj` in Xcode or compile via command line:

```bash
# Build for iOS Simulator (recommended for local development without signing certificates)
xcodebuild -project iosApp/wled.xcodeproj -scheme wled -destination 'generic/platform=iOS Simulator' build

# Or build without code signing
xcodebuild -project iosApp/wled.xcodeproj -scheme wled build CODE_SIGNING_ALLOWED=NO
```

### 3. Run Tests
```bash
xcodebuild test \
  -project iosApp/wled.xcodeproj \
  -scheme wled \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=latest' \
  -skipPackagePluginValidation \
  -skipMacroValidation \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO
```

### 4. Code Quality & Linting
Swift style is enforced using **SwiftLint** (configured via `.swiftlint.yml`):

```bash
# Within the iosApp directory
swiftlint lint

# Or from the repository root
swiftlint lint --config iosApp/.swiftlint.yml iosApp
```

## Contributing & Governance

For contribution workflows, code quality guidelines, code of conduct, and licensing details, refer to the root repository documentation:
- [Contributing Guide](../CONTRIBUTING.md)
- [Code of Conduct](../CODE_OF_CONDUCT.md)
- [License (GPL-3.0)](../LICENSE)
- [Privacy Policy](../PRIVACY.md)

## Disclaimer

This project is not an official Google project. It is not supported by Google and Google specifically disclaims all warranties as to its quality, merchantability, or fitness for a particular purpose.

Apple, the Apple logo, iPhone, and iPad are trademarks of Apple Inc., registered in the U.S. and other countries. App Store is a service mark of Apple Inc.
