---
name: translate-changelog
description: >-
  Promote and translate "What's New" release notes (<version>.md -> <version>.<lang>.md
  and short Google Play summaries in fastlane/changelogs/<version>.<lang>.txt) across
  Android and iOS using the app's existing localized UI strings and WLED terminology.
  Trigger on /translate-changelog or when preparing release changelogs or adding new changelog translations.
---

# Translate Changelog Skill (`/translate-changelog`)

Use this skill when promoting `dev.md` to a release version (e.g., `8.1.0`) or translating existing `<version>.md` in-app changelogs and `fastlane/changelogs/<version>.txt` Google Play Store summaries into the languages supported by the Android and iOS apps.

---

## 1. Core Principles

1. **English is the Single Source of Truth:**
   - During active development, changes are only added to `app/src/main/assets/changelog/dev.md` and `iosApp/wled/changelog/dev.md` in English.
   - Do **not** create `dev.<lang>.md` or `fastlane/changelogs/dev*.txt` files during normal feature PRs unless explicitly asked.
2. **Base Language Codes Only (No Regional Variants):**
   - Name translated files `<version>.<lang>.md` (and `<version>.<lang>.txt` in `fastlane/changelogs/`) using the lowercase 2–3 letter ISO 639 base language code (e.g., `8.1.0.fr.md`, `8.1.0.de.md`, `8.1.0.zh.md`) in the same directory as `<version>.md` / `<version>.txt`.
   - Never use subdirectories or regional suffixes (`8.1.0.fr-CA.md`), as `ChangelogLogic` and `fastlane/Fastfile` normalize/map base language codes (`fr`, `de`, `zh`) to their store and device locales.
3. **Exact UI String Alignment:**
   - Before translating, inspect the target language's UI strings so any referenced screen, button, or setting name matches the app verbatim:
     - **Android:** `app/src/main/res/values-<lang>/strings.xml`
     - **iOS:** `iosApp/wled/Localizable.xcstrings`
4. **Google Play 500-Character Limit (`fastlane/changelogs/`):**
   - While in-app changelogs (`app/src/main/assets/changelog/<version>*.md`) have no character limit, Google Play Store release notes (`fastlane/changelogs/<version>*.txt`) enforce a strict **500-character limit** per locale (measured as stripped UTF-8 code points).

---

## 2. Discovering Target Languages

Determine the target languages dynamically from the repository (unless the user specifies a subset):

- **Android (`app/src/main/assets/changelog/` & `fastlane/changelogs/`):**
  - Inspect `app/src/main/res/xml/locales_config.xml`, `app/src/main/res/values-<lang>/strings.xml` (excluding `en`), and `PLAY_STORE_LOCALE_MAP` in `fastlane/Fastfile`.
  - Current non-English languages: `fr`, `de`, `zh`.
- **iOS (`iosApp/wled/changelog/`):**
  - Inspect `knownRegions` in `iosApp/wled.xcodeproj/project.pbxproj` and language keys in `iosApp/wled/Localizable.xcstrings` (excluding `en` and `Base`), normalized to their base language code (`fr-CA` -> `fr`, `zh-Hans` -> `zh`).
  - Current non-English languages: `fr`, `de`, `zh`.

---

## 3. Workflow Steps

### Step 1: Promote `dev.md` and Create `fastlane/changelogs/<version>.txt` (If Preparing a Release)
If the user provides a target release version (e.g., `8.1.0`) and `dev.md` has not been renamed yet:
1. Rename `app/src/main/assets/changelog/dev.md` -> `app/src/main/assets/changelog/<version>.md`.
2. Rename `iosApp/wled/changelog/dev.md` -> `iosApp/wled/changelog/<version>.md`.
3. Create `fastlane/changelogs/<version>.txt` by summarizing the 2–5 most important user-facing highlights from `app/src/main/assets/changelog/<version>.md` into plain-text `- ` bullets targeting 350–450 characters (see `fastlane/changelogs/README.md` and `fastlane/changelogs/7.0.0.txt`).

### Step 2: Read Source Changelogs & UI Terminology
1. Read the English `<version>.md` in `app/src/main/assets/changelog/`, `iosApp/wled/changelog/`, and `fastlane/changelogs/<version>.txt`.
2. Look up any quoted or capitalized UI labels mentioned in the changelog inside `app/src/main/res/values-<lang>/strings.xml` and `iosApp/wled/Localizable.xcstrings`.

### Step 3: Apply WLED Domain Glossary & Formatting Rules
- **In-App Markdown Formatting Rules (`<version>.<lang>.md`):**
  - Preserve `###` section headings and bullet structure (`- `).
  - Never add `#` top-level version headers (the app renders the version title on the card header).
  - Never add emojis in headings or at the start of list items.
  - Translate the final `### Other Improvements` / `### Other Improvements & Fixes` heading naturally (keep these localized heading prefixes in sync with `OTHER_IMPROVEMENTS_HEADING_REGEX` in `fastlane/Fastfile`):
    - `fr`: `### Autres améliorations` / `### Autres améliorations et correctifs`
    - `de`: `### Weitere Verbesserungen` / `### Weitere Verbesserungen & Fehlerbehebungen`
    - `zh`: `### 其他改进` / `### 其他改进与修复`
- **Google Play Plain-Text Formatting Rules (`fastlane/changelogs/<version>.<lang>.txt`):**
  - Plain text only: use `- ` bullets without `###` headings, `**bold**` markers, or backticks.
  - Keep bullets concise and high-impact so every localized file stays `<= 500` characters.
- **WLED & Technical Glossary:**
  - Keep brand/project names unchanged: **WLED**, **QuinLED**, **MoonModules**, **PixelForge**, **Material 3**, **Siri**, **WebSocket**, **WebView**, **mDNS**, **OTA**, `wled://`.
  - For French (`fr`), write in natural Québec French (`fr-CA` tone and vocabulary, avoiding France-specific idioms or anglicisms, and following Québec typography with no space before `!` or `?`).
  - Common UI terms:
    - *Settings* -> `Paramètres` (`fr`), `Einstellungen` (`de`), `设置` (`zh`)
    - *Theme (System Default / Light / Dark)* -> `Thème (Par défaut / Clair / Sombre)` (`fr`), `Thema (Systemeinstellungen / Hell / Dunkel)` (`de`), `主题（系统默认 / 亮色 / 暗色）` (`zh`)
    - *Automatically Discover New Devices* -> `« Découvrir les nouveaux appareils automatiquement »` (`fr`), `„Neue Geräte automatisch erkennen“` (`de`), `“自动寻找新设备”` (`zh`)
    - *Show Offline Devices Last* -> `« Afficher les appareils hors-ligne en dernier »` (`fr`), `„Zeige Geräte, die offline sind, als letztes an“` (`de`), `“离线设备显示在底部”` (`zh`)
    - *Edit Device* -> `Modifier l'appareil` (`fr`), `Gerät bearbeiten` (`de`), `编辑设备` (`zh`)

### Step 4: Write `<version>.<lang>.md` and `<version>.<lang>.txt` Files
Write each translated file alongside `<version>.md` in `app/src/main/assets/changelog/` and `iosApp/wled/changelog/`, and write each translated Google Play summary alongside `<version>.txt` in `fastlane/changelogs/`.

### Step 5: Verify Google Play 500-Character Limit
Measure the stripped UTF-8 code-point count using the same check as CI and Fastlane (do not use `wc -m`, which counts trailing newlines and falls back to bytes under `LANG=C`):
```bash
python3 -c 'import pathlib,sys; [print(len(pathlib.Path(p).read_text("utf-8").strip()), p) for p in sys.argv[1:]]' fastlane/changelogs/<version>*.txt
```
- Verify that the English `fastlane/changelogs/<version>.txt` and every localized `fastlane/changelogs/<version>.<lang>.txt` is non-empty and strictly **`<= 500` characters**.
- Because French (`fr`) and German (`de`) often expand by 15–25% compared to English, if any translated `.txt` file exceeds 500 characters, tighten the phrasing or fold the lowest-priority bullet into the closing "fixes and improvements" bullet until every `<version>*.txt` file reports `<= 500`.
