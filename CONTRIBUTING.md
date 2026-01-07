# Contributing to WLED-Android

## Branching Strategy
* **`main`**: (Default) Stable, released version. **Do not push here directly.**
* **`dev`**: Active development branch.

## Submitting Changes
1. Fork the repository.
2. Create your feature branch off of `dev`:
   `git checkout -b my-feature dev`
3. **IMPORTANT:** When opening a Pull Request, you must change the **base branch** from `main` to **`dev`**.
   *(GitHub defaults to `main`, so please double-check this!)*

## Hotfixes
If you are fixing a critical bug in production:
1. Branch off `main`.
2. Submit a PR to `main`.
3. **Important:** You must also merge these changes back into `dev` to ensure the bug doesn't reappear in the next release.