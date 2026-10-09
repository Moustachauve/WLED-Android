# Contributing to WLED-Native-iOS

## Branching Strategy
* **`main`**: (Default) Stable, released version. **Do not push here directly.**
* **`dev`**: Active development branch.

## Submitting Changes
1. Fork the repository.
2. Create your feature branch off of `dev`:
   `git checkout -b my-feature dev`
3. **IMPORTANT:** When opening a Pull Request, you must change the **base branch** from `main` to **`dev`**.
   *(GitHub defaults to `main`, so please double-check this!)*

## Code Quality & Linting
This project uses **SwiftLint** to enforce Swift style and conventions.
Before submitting a Pull Request, please ensure that your code passes all lint checks.

### Installing SwiftLint Locally
To easily catch linting errors during development, we highly recommend installing SwiftLint:

**Via Homebrew:**
```bash
brew install swiftlint
```

You can run `swiftlint lint` in the root of the repository to see any warnings or errors. 
Our `.swiftlint.yml` configuration file defines the active rules. By default, the CI pipeline will block Pull Requests that contain SwiftLint errors.

## Pull Request Labels
To ensure release notes are generated correctly, please add appropriate labels to your Pull Request. The automation relies on these labels to categorize changes and determine the version number.

- **For categorization:** `feature`, `enhancement`, `bug`, `fix`, `documentation`, `chore`, `refactor`.
- **For versioning:** `major` (for breaking changes), `minor` (for features), `patch` (for fixes).

## Hotfixes
If you are fixing a critical bug in production:
1. Branch off `main`.
2. Submit a PR to `main`.
3. **Important:** You must also merge these changes back into `dev` to ensure the bug doesn't reappear in the next release.
