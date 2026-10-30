# Google Play Store Changelogs

This directory contains **short, plain-text release notes** specifically curated for the **Google Play Store "What's New" section**, which enforces a strict **500-character limit** per locale (`GOOGLE_PLAY_MAX_CHANGELOG_CHARS = 500`).

The full, rich Markdown release notes displayed inside the Android app's "What's New" bottom sheet live separately in [`app/src/main/assets/changelog/`](../../app/src/main/assets/changelog/README.md) and are not subject to the 500-character limit.

## Rules

1. **Strict 500-Character Limit:** Every `.txt` file in this directory (including translations) **must** be `<= 500` Unicode code points after stripping leading/trailing whitespace. Target **350–450 characters** in English so translated versions (`fr`, `de`) have comfortable headroom under 500 characters. Measure character counts with the same UTF-8 `.strip()` check used by CI and Fastlane (do not rely on `wc -m`, which counts the trailing newline and falls back to byte counts under `LANG=C`):
   ```bash
   python3 -c 'import pathlib,sys; [print(len(pathlib.Path(p).read_text("utf-8").strip()), p) for p in sys.argv[1:]]' fastlane/changelogs/*.txt
   ```
2. **Plain Text Only:** Google Play does not render Markdown in release notes. Use plain `- ` bullet points without `#` headings, `**bold**` markers, or backticks.
3. **Curate the Most Important Highlights:**
   - Do not try to squeeze every minor tweak from `app/src/main/assets/changelog/<version>.md` into the Play Store summary.
   - Pick the **2–5 most impactful user-facing features and critical bug fixes**, written as punchy one-line bullets.
   - Condense remaining minor tweaks and bug fixes into a single closing bullet if space permits (e.g., `- Bug fixes and stability improvements.`).
4. **Naming Convention & Reference Examples:**
   - Production releases: `<version>.txt` (e.g., `8.1.0.txt`) and localized `<version>.<lang>.txt` (`8.1.0.fr.txt`, `8.1.0.de.txt`, `8.1.0.zh.txt`).
   - `7.0.0.txt` and `7.0.0.{fr,de,zh}.txt` are retained in this directory as style and length reference examples paired with [`app/src/main/assets/changelog/7.0.0*.md`](../../app/src/main/assets/changelog/7.0.0.md).
5. **When to Create / Update:**
   - Do **not** keep a standing `dev.txt` file on `dev` between releases; routine feature PRs only update `app/src/main/assets/changelog/dev.md` (and `iosApp/wled/changelog/dev.md`), and Beta builds automatically summarize `app/src/main/assets/changelog/dev.md` in `fastlane/Fastfile` so beta notes never go stale across PRs.
   - Create `<version>.txt` and `<version>.<lang>.txt` during production release preparation using `/prepare-release` and `/translate-changelog`.
