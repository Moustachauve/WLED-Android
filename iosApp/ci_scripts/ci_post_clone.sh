#!/bin/sh
set -e

defaults write com.apple.dt.Xcode IDESkipPackagePluginFingerprintValidatation -bool YES
defaults write com.apple.dt.Xcode IDESkipMacroFingerprintValidation -bool YES

# Navigate to monorepo root and assemble the shared Kotlin Multiplatform XCFramework
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if ! java -version >/dev/null 2>&1; then
    brew install openjdk@17
    export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

./gradlew :shared:assembleSharedReleaseXCFramework --no-daemon
