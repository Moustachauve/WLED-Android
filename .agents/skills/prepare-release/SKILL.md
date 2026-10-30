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
   - Find the latest published **stable** release tag and its publication date:
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

1. **Gather All Changes Since the Last Stable Tag:**
   - List all commits and merged PRs on `dev` since `<last-stable-tag>` (filtering server-side by `<last-stable-publishedAt>`):
     ```bash
     git fetch origin dev main --tags
     git log <last-stable-tag>..origin/dev --oneline --no-merges
     gh pr list --state merged --base dev --search "merged:>=<last-stable-publishedAt>" --limit 500 --json number,title,labels,author,mergedAt
     ```
2. **Cross-Check Against Platform `dev.md` Files:**
   - Read the `dev.md` changelog file for each supported platform (listed under **Key Configuration Files** in `AGENTS.md`).
   - Verify whether any user-facing features, enhancements, or notable bug fixes were missed on any platform (accounting for shared changes vs. platform-specific changes).

---

## Phase 3A: Beta Release Workflow (`beta`)

1. **Update Cumulative `dev.md` Files (If Needed):**
   - `dev.md` is **cumulative**: it contains everything new since the last **stable** release.
   - If the Phase 2 audit found missing user-facing items, update the affected platform `dev.md` file(s) following the changelog style rules in `AGENTS.md` and each changelog directory's `README.md`. Keep `dev.md` in English only and do not rename it.
   - If any `dev.md` file was modified, create a branch off `dev`, run verification checks, ask the user for confirmation, and open a PR targeting `dev`. Wait for (or remind) the user to merge that PR into `dev` before finalizing the GitHub Release draft so `.github/workflows/release-drafter.yml` finishes running first.
2. **Prepare the Incremental GitHub Beta Draft Release:**
   - Unlike `dev.md`, a Beta GitHub Release description is **incremental**:
     - For `beta.1`: includes all PRs since the last stable release (which matches the existing Release Drafter draft body from `gh release view <draft-tag> --json body -q .body`).
     - For `beta.2+`: includes **only** PRs merged into `dev` since the previous beta tag (`<prev-beta-tag>..origin/dev`).
   - Format the release body following `.github/release-drafter.yml` (`## What's New`, optional brief beta summary, `## 🚀 New Features`, `## 🐛 Bug Fixes`, `## 🧰 Maintenance`, and `## Contributors` linking PR numbers and `@author` handles).
   - Present the proposed tag and release body to the user for confirmation.
   - Once confirmed, create or update the draft pre-release targeting `dev`:
     ```bash
     gh release create v<version>-beta.<N> --draft --prerelease --target dev --title "v<version>-beta.<N>" --notes-file - <<'EOF'
     <release body>
     EOF
     ```
     *(If updating an existing draft, use `gh release edit <tag> --draft --prerelease --target dev --tag v<version>-beta.<N> --title "v<version>-beta.<N>" --notes-file -`.)*

---

## Phase 3B: Full / Stable Release Workflow (`production`)

1. **Craft End-User-Friendly `<version>.md` Changelogs:**
   - Promote `dev.md` to `<version>.md` (and delete `dev.md`) in every supported platform's changelog directory.
   - Rewrite and polish each `<version>.md` for **everyday, non-technical end users** (use existing versioned changelogs such as `7.0.0.md` for tone inspiration while adhering to the formatting rules in `AGENTS.md` and the changelog `README.md` files):
     - Focus strictly on what users can **see, feel, and do**—highlight tangible new capabilities, visual polish, and noticeable fixes in warm, engaging language.
     - Strip out under-the-hood engineering details, dependency bumps, architectural refactors, and developer jargon (e.g., do not mention library upgrades, internal framework migrations, or replacing a WebView error screen with a native view; either reframe user-visible improvements in terms of user benefit or omit purely internal changes).
   - Present the proposed English `<version>.md` files for all platforms to the user and wait for approval before translating.
2. **Translate `<version>.md` into Supported Languages:**
   - Read and execute `.agents/skills/translate-changelog/SKILL.md` to generate `<version>.<lang>.md` translations across all supported platforms.
3. **Bump Version Metadata:**
   - Update each platform's version configuration files to `<version>` as documented in `docs/RELEASING.md` (Section 3, Step 1).
4. **Open the Release Preparation PR to `dev`:**
   - Run repository lint/verification checks (`./gradlew spotlessApply detekt`).
   - Present the summary and proposed commit/PR description to the user for confirmation, then commit on a release-prep branch (e.g., `chore/prepare-release-<version>`), push, and open a PR targeting `dev`.
5. **Prepare the Cumulative GitHub Release Draft (with Human Summary):**
   - A Full GitHub Release lists **all changes since the last stable release**, ignoring intermediate beta tags (you may draw inspiration from beta release notes and `<version>.md`).
   - Start from the cumulative Release Drafter output (`gh release view <draft-tag> --json body -q .body` or all PRs merged since `<last-stable-tag>`), keeping the categorized PR lists (`## 🚀 New Features`, `## 🐛 Bug Fixes`, `## 🧰 Maintenance`) and `## Contributors` from `.github/release-drafter.yml`.
   - Insert a concise, **human-friendly highlight summary** directly under `## What's New` at the top of the release notes before the categorized PR lists.
   - Present the proposed GitHub Release draft (`v<version>`) to the user for confirmation, then create or update the draft targeting `main` (`--draft --prerelease=false --target main --title "v<version>"`).
   - *Important Note on Release Drafter:* Because `.github/workflows/release-drafter.yml` runs automatically on pushes to `dev` and `main` and overwrites the matching draft body with its raw template, apply (or re-apply) the custom `gh release edit v<version>` draft body after the `dev` -> `main` PR is merged, right before publishing the release.
