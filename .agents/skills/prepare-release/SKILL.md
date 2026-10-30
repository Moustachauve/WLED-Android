---
name: prepare-release
description: >-
  Prepare a Beta or Full (Stable) release across all supported platforms: audit merged PRs
  and commits since the last stable release, update or rewrite platform changelogs (and
  translations via /translate-changelog), open a release-prep PR to dev, and prepare the
  GitHub Release draft. Trigger on /prepare-release or when the user asks to prepare a beta
  or stable release.
---

# Prepare Release Skill (`/prepare-release`)

Use this skill to prepare either a **Beta (`beta`)** or a **Full / Stable (`production`)** release across all supported platforms. Follow the repository's rules, platform paths, and conventions in `AGENTS.md`, `CONTRIBUTING.md`, `docs/RELEASING.md`, and each platform's changelog `README.md` rather than duplicating formatting or git rules here.

---

## Phase 1: Discover Release Context & Confirm Target Version

1. **Inspect Existing Releases & Drafts:**
   - Find the latest published **stable** release tag and its publication timestamp (`<last-stable-publishedAt>`):
     ```bash
     gh release list --exclude-pre-releases --exclude-drafts --limit 1 --json tagName,publishedAt
     ```
   - List recent releases (including pre-releases and Release Drafter drafts):
     ```bash
     gh release list --limit 10 --json name,tagName,isDraft,isPrerelease,publishedAt
     ```
2. **Confirm Track & Version:**
   - If the release track (`beta` vs. `production`) or target version/tag (e.g., `v8.0.0-beta.1` or `v8.0.0`) is ambiguous, ask the user via `ask_question` with a recommended tag derived from the existing Release Drafter draft or recent PR labels.

---

## Phase 2: Audit PRs & Commits Since the Last Stable Release

1. **Gather All Changes Since the Last Stable Release:**
   - Use the date-filtered merged PR list as the primary source of truth, supplemented by a `--since` commit log check (do **not** use `git log <last-stable-tag>..origin/dev`, as stable tags on `main` are not always direct git ancestors of `dev` due to squash/merge history and will include old pre-tag commits):
     ```bash
     git fetch origin dev main --tags
     gh pr list --state merged --base dev --search "merged:>=<last-stable-publishedAt>" --limit 500 --json number,title,labels,author,mergedAt
     git log origin/dev --no-merges --since="<last-stable-publishedAt>" --oneline
     ```
2. **Cross-Check Against Platform `dev.md` Files:**
   - Read the `dev.md` changelog file for each supported platform (listed under **Key Configuration Files** in `AGENTS.md`).
   - Verify whether any user-facing features, enhancements, or notable bug fixes were missed on any platform (accounting for shared changes vs. platform-specific changes).

---

## Shared Guideline: Release Drafter & GitHub Release Drafts

- **Draft Format (`.github/release-drafter.yml`):**
  - Starts with `## What's New` (with an optional human-friendly summary paragraph directly below it), followed by the categorized PR sections (`## 🚀 New Features`, `## 🐛 Bug Fixes`, `## 🧰 Maintenance`), where each bullet is formatted as `* <PR Title> (#<number>) @<author>`.
  - Any unlabeled PRs appear uncategorized at the top of `$CHANGES` in Release Drafter's output—either classify them into the matching section (`🚀 New Features`, `🐛 Bug Fixes`, or `🧰 Maintenance`) or keep them under `## What's New` so no PR is silently dropped.
  - Ends with `## Contributors` listing the `@<author>` handles.
- **Preventing Release Drafter Overwrites:**
  - `.github/workflows/release-drafter.yml` runs on every push to `dev` and `main` and overwrites the most recent GitHub Release draft's tag, title, and body back to its cumulative template.
  - Therefore, always wait until any release-prep PR on `dev` (or the `dev` -> `main` merge PR for production) is merged and the `Release Drafter` Action finishes before applying your final `gh release create` / `gh release edit` draft updates right before publishing.

---

## Phase 3A: Beta Release Workflow (`beta`)

1. **Update Cumulative `dev.md` Files (If Needed):**
   - `dev.md` is **cumulative**: it contains everything new since the last **stable** release.
   - If the Phase 2 audit found missing user-facing items, update the affected platform `dev.md` file(s) following the changelog style rules in `AGENTS.md` and each changelog directory's `README.md`. Keep `dev.md` in English only and do not rename it (for Beta builds on Google Play, `fastlane/Fastfile` automatically summarizes `app/src/main/assets/changelog/dev.md` to `<= 500` characters, so do not commit a standing `fastlane/changelogs/dev.txt` on `dev`).
   - If any `dev.md` file was modified, create a branch off `dev`, run verification checks, ask the user for confirmation, and open a PR targeting `dev`.
2. **Prepare the Incremental GitHub Beta Draft Release:**
   - Unlike `dev.md`, a Beta GitHub Release description is **incremental**:
     - For `beta.1`: includes all PRs since `<last-stable-publishedAt>` (which matches the existing Release Drafter draft body from `gh release view <draft-tag> --json body -q .body`).
     - For `beta.2+`: includes **only** PRs merged into `dev` since the previous beta release's `publishedAt` timestamp (`gh pr list --state merged --base dev --search "merged:>=<prev-beta-publishedAt>" --limit 500 --json number,title,labels,author,mergedAt`).
   - Format the release body and contributors list following the **Shared Guideline** above.
   - Present the proposed tag and release body to the user for confirmation.
   - Once confirmed (and after any `dev.md` PR on `dev` is merged), create or update the draft pre-release targeting `dev`:
     ```bash
     gh release create v<version>-beta.<N> --draft --prerelease --target dev --title "v<version>-beta.<N>" --notes-file - <<'EOF'
     <release body>
     EOF
     ```
     *(If updating an existing draft, use `gh release edit <draft-tag> --draft --prerelease --target dev --tag v<version>-beta.<N> --title "v<version>-beta.<N>" --notes-file -`.)*

---

## Phase 3B: Full / Stable Release Workflow (`production`)

1. **Craft End-User-Friendly `<version>.md` Changelogs & Short Google Play Notes (`fastlane/changelogs/<version>.txt`):**
   - Promote `dev.md` to `<version>.md` (and delete `dev.md`) in every supported platform's changelog directory.
   - Rewrite and polish each `<version>.md` for **everyday, non-technical end users** (use existing versioned changelogs such as `7.0.0.md` for tone inspiration while adhering to the formatting rules in `AGENTS.md` and the changelog `README.md` files):
     - Focus strictly on what users can **see, feel, and do**—highlight tangible new capabilities, visual polish, and noticeable fixes in warm, engaging language.
     - Strip out under-the-hood engineering details, dependency bumps, architectural refactors, and developer jargon (e.g., do not mention library upgrades, internal framework migrations, or replacing a WebView error screen with a native view; either reframe user-visible improvements in terms of user benefit or omit purely internal changes).
   - **Craft the Short Google Play Summary (`fastlane/changelogs/<version>.txt`):**
     - Create `fastlane/changelogs/<version>.txt` (and delete `fastlane/changelogs/dev.txt` if present).
     - Summarize only the **2–5 most impactful user-facing features and noticeable fixes** from `app/src/main/assets/changelog/<version>.md` as concise plain-text `- ` bullets (no Markdown headers, `**bold**`, or backticks; see `fastlane/changelogs/README.md` and `fastlane/changelogs/7.0.0.txt`). Group minor tweaks and remaining fixes into a single short closing bullet so nothing gets cut off.
     - Verify the character count using the UTF-8 `.strip()` check (`python3 -c 'import pathlib,sys; [print(len(pathlib.Path(p).read_text("utf-8").strip()), p) for p in sys.argv[1:]]' fastlane/changelogs/<version>.txt`): must be `<= 500` characters, and target **350–450 characters** in English so translated versions (`fr`, `de`) have comfortable headroom under 500 characters.
   - Present the proposed English `<version>.md` files for all platforms **and** `fastlane/changelogs/<version>.txt` (with its character count) to the user and wait for approval before translating.
2. **Translate `<version>.md` and `fastlane/changelogs/<version>.txt` into Supported Languages:**
   - Read `.agents/skills/translate-changelog/SKILL.md` and execute Steps 2–5 (skipping Step 1 since `<version>.md` and `<version>.txt` were already prepared above) to generate `<version>.<lang>.md` translations across all supported platforms and `<version>.<lang>.txt` translations in `fastlane/changelogs/` (each verified to be `<= 500` characters).
3. **Bump Version Metadata:**
   - Update each platform's version configuration files to `<version>` as documented in `docs/RELEASING.md` (Section 3, Step 1).
4. **Open the Release Preparation PR to `dev`:**
   - Run repository lint/verification checks (`./gradlew spotlessApply detekt`).
   - Present the summary and proposed commit/PR description to the user for confirmation, then commit on a release-prep branch (e.g., `chore/prepare-release-<version>`), push, and open a PR targeting `dev`.
5. **Prepare the Cumulative GitHub Release Draft (with Human Summary):**
   - A Full GitHub Release lists **all changes since the last stable release**, ignoring intermediate beta tags (you may draw inspiration from beta release notes and `<version>.md`).
   - Start from the cumulative Release Drafter output (`gh release view <draft-tag> --json body -q .body` or all PRs merged since `<last-stable-publishedAt>`) following the **Shared Guideline** above.
   - Insert a concise, **human-friendly highlight summary** directly under `## What's New` at the top of the release notes before the categorized PR lists.
   - Present the proposed GitHub Release draft (`v<version>`) to the user for confirmation, then create or update the draft targeting `main` (`--draft --prerelease=false --target main --title "v<version>"`), keeping in mind the Release Drafter overwrite note in the **Shared Guideline** when merging `dev` -> `main`.
