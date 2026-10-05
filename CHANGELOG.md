# 📜 Changelog

All notable changes to **Xcode** are documented in this file.
The format loosely follows [Keep a Changelog](https://keepachangelog.com/), and versioning follows
[SemVer](https://semver.org/) with pre-release identifiers (`-alpha.N`, `-beta.N`, `-rc.N`) leading up to
stable releases.

When cutting a release, rename `## [Unreleased]

## [1.0.0] - 2026-10-05

🎉 **First stable release.** Everything below ships in 1.0.0.

### ✨ Highlights
- 📝 Sora-based multi-tab editor with TextMate highlighting, find/replace, symbol bar and autocomplete.
- 🌳 Native Git on JGit — clone, commit, branches, history, GitHub-style diff, merge, rebase, cherry-pick, stash, tags and conflict resolution.
- 📂 Project-first workflow: Home with hero banner, pin / rename / delete / info / backup per project, Open Project sheet with browse shortcuts.
- 👀 Markdown / HTML / SVG / image / video / Android Vector XML previews and fuzzy file + full-workspace code search.
- 🎨 Material 3 Expressive UI with five themes, an AMOLED mode and editor theme families.
- ℹ️ New **About** screen and a **Scroll long file names** setting for the file tree.
- 🚀 Tag-driven CI: signed per-ABI APKs, SHA-256 checksums and notes from this changelog.

### ✨ Added

#### 📂 Projects & files
- 🏠 **Home screen** with an animated hero banner (floating glass panels), recent projects, a New Project sheet and a dedicated **All projects** screen with search and sort.
- 📌 **Pin projects to the top** of recents (Home, All projects and the Open Project sheet). Pinned projects survive the 50-entry recents trim and stay on top under every sort order.
- ✏️ **Rename project folders on disk** straight from the card menu; recents, pins and the open workspace follow the new path.
- 🗑️ **Delete project folders** behind a confirmation dialog. The project disappears from recents, its pins are dropped, and if it was the open workspace the app falls back to the device root. The device root, Documents and Downloads are protected and can never be deleted from here.
- ℹ️ **Project info dialog** — full path, size, file count, last modified, last opened and the Git repository / current branch (or detached commit). Folders over 200 000 files are reported as `N+` instead of stalling.
- 📋 **Copy path** on project cards, with a snackbar confirmation below Android 13.
- 🗂️ **Open Project sheet** with search, typed paths, recursive folder search across internal storage, editable **Browse shortcuts** (show / hide, reorder, custom folders), a persisted *Show hidden* toggle and **Back up as ZIP** for recent projects.
- 📂 **File tree** with lazy loading, persisted expansion, file operations (create / rename / delete / move / copy / numbered duplicate), pinned files, a Git status stripe and a persisted *Show hidden files* toggle (eye icon).
- ↔️ **Scroll long file names** (Settings → Behavior, on by default) — the file tree scrolls sideways as one block, but only while some visible name is wider than the screen; otherwise it stays vertical-only. Turning it off brings back the ellipsised names.
- 🔁 Workspace top bar with a project-name chip dropdown (switch among recents) and external change watcher for files modified outside the app.
- 🧭 Onboarding stepper for storage access and notifications.

#### 📝 Editor
- 🗂️ Multi-tab editor with per-tab state, tab reorder, pin, close others / all, unsaved dialog and **session restore** (*Restore open files* setting).
- 🔎 Find/replace (case / whole word / regex) with match flash, go to line, auto-indent, bracket matching, word wrap, pinch-to-zoom (font slider 20–64 px).
- ⌨️ Customisable **symbol bar** (hidden by default, toggle in Settings) with pair-cursor placement.
- ⚡ **Quick actions** menu: select all, cut / copy / paste, duplicate / delete / move line, comment toggle (incl. XML / CSS block comments), **Copy file content**.
- 💡 Keyword, word and learned-word autocomplete; custom text-selection popup with labels; undo / redo glow; stronger selection highlight.
- 🔄 External-change prompt (*Reload / keep my edits*) with an optional auto-reload mode; paged loading for large files; an *Auto-preview* setting for Markdown / HTML.
- 🎨 TextMate syntax highlighting for C, C++, CSS, Dart, Groovy, HTML, Java, JavaScript, JSON, Kotlin, Markdown, Properties, Python, Shell, Smali, TOML, XML and YAML, with light/dark editor theme pairs and theme families (One Dark Pro, GitHub — the default —, Solarized, Monokai, Ayu, Darcula, VS Code, Xcode).
- 📌 Fixed, swipeable editor top bar with pinned search / save.

#### 🌳 Git (JGit, HTTPS + token)
- ⬇️ Clone with Termux-style live progress (remote lines, MiB, speed, scrolling log) and a private-repo token prompt; status, stage / unstage, commit (+ amend), push / pull / fetch (all remotes, stale remote-tracking refs pruned), discard changes.
- 🚀 Force push with confirmation, **force-with-lease**, pull-rebase, a push-rejected dialog, and push / pull intent preserved across a token-save detour.
- 🌿 Branches (also before the first commit), quick-switch dropdown, redesigned shared **merge / rebase branch picker**, tags, stash, multi-remote manager and cherry-pick.
- 🔀 **Merge** with file-level and block-level conflict resolution, *Mark all resolved*, conflict cards, shared rebase / merge banner, abort / complete; **Rebase** onto a branch with continue / skip / abort.
- 🕘 History and file history with search, **commit detail** screen (author change, hash copy, author grouping).
- 🧾 **GitHub-style diff viewer**: unified and side-by-side views, old / new gutters, hunk headers, word highlights, `+N / −M` stats, GitHub light / dark palette; tap a change to open its diff, pinch-zoom and edit inline with diff tint; image diff preview.
- 🪄 Commit box with paste button and *auto add / commit / push*; source-control drawer and bottom sheet with a badge; guided **Git onboarding** wizard (init → identity → remote → first commit → upstream).
- 🔐 Per-remote token vault (AES/GCM key in the Android Keystore), global GitHub settings with profile cards (24 h refresh, 72 h avatar cache, identity auto-fill).
- 🕒 *Git status detection* toggle (off by default) for the file-tree stripes, and per-operation **Git logs** with wait / run timings.

#### 🔎 Search
- 🔍 **File search** (fuzzy, recents, extension / folder filters) and **code search** (regex, case, whole word, include / exclude globs, parallel scan, streamed grouped results, a `text//name` file filter) with separate histories and a collapsible filters panel. Result headers show the **file name** with the folder path as a small subtitle, shortened from the outside in (`…/shared-youtube/lib`) so it always fits on one line.
- 🗂️ Recursive filename search inside the workspace tree and *locate in file tree*.
- ⚙️ Search settings: default-strings-only (skip `values-*`), extra excludes and a max file size.

#### 👀 Preview
- 📄 Markdown, HTML (split mode, JS off by default), SVG, images and video as in-editor tabs.
- 🤖 **Android Vector XML preview**: `.xml` files with a `<vector>` root open with the Editor / Split / Preview toggle and render live (groups, clip-paths, strokes, fill / stroke alpha, `#AARRGGBB`). Buttons save a **PNG** (1024 px long side) or **SVG** into `Downloads/` (never overwriting) or copy the SVG text.

#### 🎨 UI & settings
- 🎨 **Material 3 Expressive** conversion: Material Symbols Rounded icons, expressive motion, system-bar tint, splash match, five app themes (Default, Aurora, Catppuccin, Nord, Rose Pine) and a true-black AMOLED toggle.
- ⚙️ **Settings** with category cards (Appearance, Editing, Behavior, Diagnostics, GitHub, About).
- ℹ️ **About** screen — animated identity card with version, GitHub and Libraries buttons, Support (UPI), GitHub contributors (cached 72 h) and the AGPL-3.0 notice, plus a **Libraries** screen listing what Xcode is built on. English and Hindi strings.
- 🩺 On-device crash handler and crash / Git log viewers under Settings → Diagnostics.

#### 🛠️ CI & docs
- 🛠️ GitHub Actions workflows: **Android build** (every push to `main` / PR), **Make release** (`vX.Y.Z` tags) and **Make pre-release** (`vX.Y.Z-suffix` tags), all with a manual *Run workflow* option.
- 📄 README and this changelog.

### 🎨 Changed
- 🏷️ **Versioning is driven by the tag**: `v1.2.3` sets `versionName` to `1.2.3` and `versionCode` is derived from the tag (`.github/scripts/version-from-tag.sh`) instead of the CI run number, so pre-releases and stable builds always compare correctly and a beta updates into its final release. Local builds default to `1.0.0`.
- 🧱 Release signing and note generation live in `.github/scripts/` (`sign-apks.sh`, `release-notes.py`), shared by every workflow. Stable and pre-release builds refuse to publish unsigned APKs.
- 🗄️ Room database is at version 3 (adds the `pinned` column to recent projects; existing data is migrated in place).
- 🧼 **UI polish**: compact project card menu with icons, Home search only with more than 9 recents, a folder-download Clone icon, borderless paste button, hero banner without an outline, one shared spacing rhythm, a top bar that matches the page colour, content-height Source Control sheet, larger back icons and a *Reset* that defaults to hard.
- ⚡ **Git performance**: one shared `GitSession` across screens, tuned JGit `WindowCache`, batched resolve / stage, per-path status patching instead of full refreshes, and a progress dialog for long operations.
- 🪶 JGit is pinned to **5.13.5 LTS** (7.x crashed with `readNBytes` on Android < 13).
- 📦 CI and releases produce three APKs: `arm64-v8a`, `armeabi-v7a` and `universal`.

### 🐞 Fixed
- 💥 Crash in file search (`FileSearchViewModel` factory) and duplicate `LazyColumn` keys in the Git change list.
- 📱 Status-bar overlap in the file search overlay; keyboard reappearing after dismissing the Open Project sheet.
- 🧭 Opening a file from search or Git bouncing back to the previous screen (the editor was navigated to before the tab existed).
- 🔎 Find crash and freeze on very large files (debounced, count capped and run off the main thread).
- 🪟 Diff viewer dropping added lines for modified rows and treating removed `--` lines as file headers.
- 🎚️ Font slider now clamps to the pinch-zoom range; theme resources no longer contain `--` inside XML comments.

### 🗑️ Removed
- 🚫 **x86 and x86_64 builds** — no APKs are built, uploaded or released for them, and the matching ABI splits are gone from `app/build.gradle.kts`.
- 🔋 The battery-optimisation step from onboarding and the *Go to file* entry from the overflow menus.
- 🗺️ The milestone checklist from the README (replaced by this changelog).
- 🔖 The old single combined build/release workflow and its `v*` tag trigger in `android-build.yml`.
