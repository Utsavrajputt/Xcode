# 📜 Changelog

All notable changes to **Xcode** are documented in this file.
The format loosely follows [Keep a Changelog](https://keepachangelog.com/), and versioning follows
[SemVer](https://semver.org/) with pre-release identifiers (`-alpha.N`, `-beta.N`, `-rc.N`) leading up to
stable releases.

When cutting a release, rename `## [Unreleased]` to `## [x.y.z] - YYYY-MM-DD` and start a fresh
`## [Unreleased]` above it. The release workflows pick the section matching the tag (falling back to
`[Unreleased]`) as the GitHub Release notes.

## [Unreleased]

🚧 Nothing has been tagged yet — this is everything that will ship in the first release.

### ✨ Highlights
- 📂 Project cards on Home / All projects now have a full actions menu: **pin, rename, delete, project info, copy path**.
- 📝 Sora-based multi-tab editor with TextMate highlighting, find/replace, symbol bar and autocomplete.
- 🌳 Native Git on JGit — clone, commit, branches, history, diff, merge, cherry-pick, stash, tags and conflict resolution.
- 👀 Markdown / HTML / SVG / image / video previews and fuzzy file + full-workspace code search.
- 🎨 Material 3 Expressive UI with five themes and an AMOLED mode.
- 🚀 Tag-driven CI: signed per-ABI APKs, SHA-256 checksums and notes from this changelog.

### ✨ Added
- 📌 **Pin projects to the top** of recents (Home, All projects and the Open Project sheet). Pinned projects survive the 50-entry recents trim and stay on top under every sort order.
- ✏️ **Rename project folders on disk** straight from the Home / All projects card menu; recents, pins and the open workspace follow the new path.
- 🗑️ **Delete project folders** from the card menu behind a confirmation dialog. The project disappears from recents, its pins are dropped, and if it was the open workspace the app falls back to the device root. The device root, Documents and Downloads are protected and can never be deleted from here.
- ℹ️ **Project info dialog** — full path, size, file count, last modified, last opened and the Git repository / current branch (or detached commit). Folders over 200 000 files are reported as `N+` instead of stalling.
- 📋 **Copy path** on project cards, with a snackbar confirmation below Android 13; All projects gained its own snackbar host and effect handling.
- 📂 File tree with lazy loading, persisted expansion, file operations (create / rename / delete / move / copy / numbered duplicate), pinned files and a Git status stripe.
- 🗂️ Open Project sheet with search, typed paths, editable browse shortcuts and **Back up as ZIP** for recent projects.
- 📝 Editor: multi-tab with per-tab state, find/replace (case / whole word / regex), go to line, auto-indent, bracket matching, word wrap, font zoom, customisable symbol bar, quick line actions, external-change prompt, paged loading for large files.
- 🎨 TextMate syntax highlighting for C, C++, CSS, Dart, Groovy, HTML, Java, JavaScript, JSON, Kotlin, Markdown, Properties, Python, Shell, Smali, TOML, XML and YAML, with light/dark editor theme pairs.
- 🌳 Git over HTTPS + token: clone, status, stage/unstage, commit (+ amend), push / pull / fetch, force push with confirmation, branch management, history and file history, inline and side-by-side diff, merge, cherry-pick, stash, tags, multi-remote credentials and inline conflict resolution.
- 🧭 Guided Git onboarding wizard (init → identity → remote → first commit → upstream).
- 🔐 Per-remote token vault using an AES/GCM key in the Android Keystore, plus GitHub profile lookups per host.
- 👀 Previews: Markdown, HTML (split mode, JS off by default), SVG, images and video as in-editor tabs.
- 🔎 File search (fuzzy, recents, filters) and code search (regex, case, whole word, include / exclude globs, streamed grouped results) with separate histories.
- 🎨 Five app themes (Default, Aurora, Catppuccin, Nord, Rose Pine) and a true-black AMOLED toggle.
- 🩺 On-device crash and Git log viewers under Settings → Diagnostics.
- 🛠️ GitHub Actions workflows: **Android build** (every push to `main` / PR), **Make release** (`vX.Y.Z` tags) and **Make pre-release** (`vX.Y.Z-suffix` tags), all with a manual *Run workflow* option.
- 📄 README and this changelog.

### 🎨 Changed
- 🏷️ **Versioning is now driven by the tag**: `v1.2.3` sets `versionName` to `1.2.3` and `versionCode` is derived from the tag (`.github/scripts/version-from-tag.sh`) instead of the CI run number, so pre-releases and stable builds from separate workflows always compare correctly and a beta updates into its final release.
- 🧱 Release signing and note generation moved into `.github/scripts/` (`sign-apks.sh`, `release-notes.py`) shared by every workflow. Stable and pre-release builds refuse to publish unsigned APKs.
- 🗄️ Room database bumped to version 3 (adds the `pinned` column to recent projects; existing data is migrated in place).
- 🧼 **UI polish pass**: borderless paste button in the commit box, hero banner without an outline, one shared edge/gap rhythm on Home, a top bar that matches the page colour (no seam while scrolling), and bottom breathing room under the Git changes lists. The editor's Source Control sheet now opens at its content height instead of half-open.
- 📦 CI and releases now produce three APKs: `arm64-v8a`, `armeabi-v7a` and `universal`.

### 🗑️ Removed
- 🚫 **x86 and x86_64 builds** — no APKs are built, uploaded or released for them any more, and the matching ABI splits are gone from `app/build.gradle.kts`.
- 🗺️ The milestone checklist from the README (replaced by this changelog).
- 🔖 The old single combined build/release workflow and its `v*` tag trigger in `android-build.yml`.
