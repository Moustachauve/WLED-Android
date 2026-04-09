# Changelog Directory

This directory contains markdown versions of the "What's New" release notes.

## Rules

1. Files should be named with their version in SemVer format. Examples: `7.1.0.md`, `v7.1.0.md` or `v8.0.0-beta.md`.
2. Do not include large headers inside the Markdown since the app automatically prepends a header with the version name.
3. Keep it brief and focused on new features!
4. It's recommended to include an empty line between bullets or paragraphs for better readability when rendered.
5. Try to keep the latest changelog up to date with your changes when creating a new PR.

## Development Changelog

When submitting new code to the repository via Pull Requests, **always append your changes to the `dev.md` file.**
Do **NOT** create a new versioned markdown file or append to an existing production version file unless explicitly asked
to do so during a release phase.

**Release Protocol:**
Before a final production build is compiled and released, a maintainer **must** rename `dev.md` to the targeted Semantic
Version string (e.g. `dev.md` -> `7.2.0.md`). The file `dev.md` should not be included in stable releases.
