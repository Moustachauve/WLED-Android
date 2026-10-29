---
name: translate-changelog
description: >-
  Promote and translate "What's New" release notes (<version>.md -> <version>.<lang>.md)
  across Android and iOS using the app's existing localized UI strings and WLED terminology.
  Trigger on /translate-changelog or when preparing release changelogs or adding new changelog translations.
---

# Translate Changelog Skill (`/translate-changelog`)

Use this skill when promoting `dev.md` to a release version (e.g., `8.1.0.md`) or translating existing `<version>.md` changelogs into the languages supported by the Android and iOS apps.

---

## 1. Core Principles

1. **English is the Single Source of Truth:**
   - During active development, changes are only added to `app/src/main/assets/changelog/dev.md` and `iosApp/wled/changelog/dev.md` in English.
   - Do **not** create `dev.<lang>.md` files during normal feature PRs unless explicitly asked.
2. **Base Language Codes Only (No Regional Variants):**
   - Name translated files `<version>.<lang>.md` using the lowercase 2–3 letter ISO 639 base language code (e.g., `8.1.0.fr.md`, `8.1.0.de.md`, `8.1.0.zh.md`) in the same directory as `<version>.md`.
   - Never use subdirectories or regional suffixes (`8.1.0.fr-CA.md`), as `ChangelogLogic` normalizes regional locales (`fr-CA` -> `fr`, `zh-Hans-CN` -> `zh`) to the base language code.
3. **Exact UI String Alignment:**
   - Before translating, inspect the target language's UI strings so any referenced screen, button, or setting name matches the app verbatim:
     - **Android:** `app/src/main/res/values-<lang>/strings.xml`
     - **iOS:** `iosApp/wled/Localizable.xcstrings`

---

## 2. Discovering Target Languages

Determine the target languages dynamically from the repository (unless the user specifies a subset):

- **Android (`app/src/main/assets/changelog/`):**
  - Inspect `app/src/main/res/xml/locales_config.xml` and `app/src/main/res/values-<lang>/strings.xml` (excluding `en`).
  - Current non-English languages: `fr`, `de`, `zh`.
- **iOS (`iosApp/wled/changelog/`):**
  - Inspect `knownRegions` in `iosApp/wled.xcodeproj/project.pbxproj` and language keys in `iosApp/wled/Localizable.xcstrings` (excluding `en` and `Base`), normalized to their base language code (`fr-CA` -> `fr`).
  - Current non-English languages: `fr`.

---

## 3. Workflow Steps

### Step 1: Promote `dev.md` (If Preparing a Release)
If the user provides a target release version (e.g., `8.1.0`) and `dev.md` has not been renamed yet:
1. Rename `app/src/main/assets/changelog/dev.md` -> `app/src/main/assets/changelog/<version>.md`.
2. Rename `iosApp/wled/changelog/dev.md` -> `iosApp/wled/changelog/<version>.md`.

### Step 2: Read Source Changelogs & UI Terminology
1. Read the English `<version>.md` in `app/src/main/assets/changelog/` and/or `iosApp/wled/changelog/`.
2. Look up any quoted or capitalized UI labels mentioned in the changelog inside `app/src/main/res/values-<lang>/strings.xml` and `iosApp/wled/Localizable.xcstrings`.

### Step 3: Apply WLED Domain Glossary & Formatting Rules
- **Formatting Rules (Must Match English Source):**
  - Preserve `###` section headings and bullet structure (`- `).
  - Never add `#` top-level version headers (the app renders the version title on the card header).
  - Never add emojis in headings or at the start of list items.
  - Translate the final `### Other Improvements` / `### Other Improvements & Fixes` heading naturally:
    - `fr`: `### Autres améliorations` / `### Autres améliorations et correctifs`
    - `de`: `### Weitere Verbesserungen` / `### Weitere Verbesserungen & Fehlerbehebungen`
    - `zh`: `### 其他改进` / `### 其他改进与修复`
- **WLED & Technical Glossary:**
  - Keep brand/project names unchanged: **WLED**, **QuinLED**, **MoonModules**, **PixelForge**, **Material 3**, **Siri**, **WebSocket**, **WebView**, **mDNS**, **OTA**, `wled://`.
  - Use international standard French (`fr`) that reads naturally in both Canada (Quebec) and Europe.
  - Common UI terms:
    - *Settings* -> `Paramètres` (`fr`), `Einstellungen` (`de`), `设置` (`zh`)
    - *Theme (System Default / Light / Dark)* -> `Thème (Par défaut / Clair / Sombre)` (`fr`), `Thema (Systemeinstellungen / Hell / Dunkel)` (`de`), `主题（系统默认 / 亮色 / 暗色）` (`zh`)
    - *Automatically Discover New Devices* -> `« Découvrir les nouveaux appareils automatiquement »` (`fr`), `„Neue Geräte automatisch erkennen“` (`de`), `“自动寻找新设备”` (`zh`)
    - *Show Offline Devices Last* -> `« Afficher les appareils hors-ligne en dernier »` (`fr`), `„Zeige Geräte, die offline sind, als letztes an“` (`de`), `“离线设备显示在底部”` (`zh`)
    - *Edit Device* -> `Modifier l'appareil` (`fr`), `Gerät bearbeiten` (`de`), `编辑设备` (`zh`)

### Step 4: Write `<version>.<lang>.md` Files
Write each translated file alongside `<version>.md` in `app/src/main/assets/changelog/` and `iosApp/wled/changelog/`, and verify that all files are non-empty and properly formatted.
