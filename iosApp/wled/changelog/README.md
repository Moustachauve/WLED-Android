# Changelog Directory

This directory contains markdown versions of the iOS "What's New" release notes.

## Rules

1. Files should be named with their version in SemVer format (`7.1.0.md`, `v7.1.0.md`, or `v8.0.0-beta.md`). Translated release notes use a lowercase base language code suffix (`8.0.0.fr.md`, `8.0.0.de.md`, `8.0.0.zh.md`) in the same directory; the app automatically falls back to `<version>.md` per version when a translation is missing.
2. Do not include `#` version headers inside the Markdown since the app automatically displays a header with the version name on each card.
3. Prefer `###` section titles with short, playful descriptions over bullet lists that start with bold words (avoid `- **Feature**: ...`). Bullet lists under a `###` title are fine if a large feature needs sub-items.
4. Group minor features, small tweaks, and bug fixes at the bottom under `### Other Improvements` (or `### Other Improvements & Fixes` when bug fixes are included) using plain bullet points.
5. Do not use emojis in headings or at the beginning of list items.
6. Keep the latest changelog up to date with your changes when creating a new PR (and also update `app/src/main/assets/changelog/dev.md` if the change applies to Android as well).

## Development Changelog

When submitting new code to the repository via Pull Requests, **always append your user-facing iOS changes to the English `dev.md` file.**
Do **NOT** create `dev.<lang>.md` translations during development, create a new versioned markdown file, or append to an existing production version file unless explicitly asked to do so during a release phase.

**Release Protocol:**
Before a final production build is compiled and released, a maintainer **must** rename `dev.md` to the targeted Semantic Version string (e.g. `dev.md` -> `8.1.0.md`) and generate the `<version>.<lang>.md` translations using the `/prepare-release` or `/translate-changelog` skill. The file `dev.md` should not be included in stable releases.
