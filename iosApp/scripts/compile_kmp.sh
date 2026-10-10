#!/bin/bash
set -euo pipefail

# Navigate to monorepo root
REPO_ROOT="$(cd "${SRCROOT:-$(pwd)}/.." && pwd)"
cd "$REPO_ROOT"

# Ensure JAVA_HOME is configured (Xcode GUI launched apps do not inherit terminal shell profiles)
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -x "/usr/libexec/java_home" ]; then
        export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
    fi
fi
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}/opt/homebrew/bin:/usr/local/bin:$PATH"

# Build debug or release XCFramework depending on Xcode configuration
if [ "${CONFIGURATION:-Debug}" = "Release" ]; then
    ./gradlew :shared:assembleSharedReleaseXCFramework
else
    ./gradlew :shared:assembleSharedDebugXCFramework
fi
