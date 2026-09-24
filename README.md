# Xcode — Lite Git + Editor

**A native Android source control + code editor.** No bundled build system, no SDK
manager, no terminal, no Ubuntu environment — just Git and a fast code editor,
built from scratch.

<p align="left">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" />
  <img src="https://img.shields.io/badge/Min%20SDK-30-blue.svg" />
  <img src="https://img.shields.io/badge/Kotlin-Compose%20%2F%20Material%203-7F52FF.svg?logo=kotlin" />
</p>

<p align="left">
  <a href="https://github.com/utsavrajputt/xcode/actions/workflows/android-build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/utsavrajputt/xcode/android-build.yml?branch=main&logo=github&label=Build" />
  </a>
</p>

> [!NOTE]
> Early development (**M2: Files & Projects** in progress). Most features below are the project's
> planned scope, not yet built — see [Milestones](#-milestones) for what's actually done.

---

## Why

[ACSIDE](https://play.google.com/store/apps) covers this niche but is closed
source. Xcode is a from-scratch, open reference implementation focused purely
on **source control + editing** — no on-device build/run, since that's a
different (and much heavier) problem best left to GitHub Actions / a PC.

---

## ✨ Planned features

<details>
<summary><b>📂 File manager & projects</b></summary>

| Feature | Description |
|---|---|
| File tree | Expand/collapse, lazy load, state preserved across sessions |
| File ops | Create, rename, delete, move, copy, duplicate (numbered), long-press context menu |
| Git status stripe | Colored indicator per file/folder — modified / added / untracked / conflicted |
| Open Project sheet | Search, manual path entry, browsable folders |
| Recent workspaces | Long-press actions: backup as ZIP, copy path, rename, delete |
| Safety | Binary/large-file open warning, hidden files toggle |

</details>

<details>
<summary><b>📝 Editor</b></summary>

| Feature | Description |
|---|---|
| Engine | [Sora Editor](https://github.com/Rosemoe/sora-editor) wrapped in Compose via `AndroidView` |
| Tabs | Multi-tab, reorder, unsaved indicator, close others/all, per-tab state (zoom, cursor, scroll, undo stack) preserved on switch |
| Editing | Find/replace (case, whole word, regex), go to line, undo/redo, auto-indent, bracket + pair matching, word wrap, font zoom |
| Autocomplete | Keyword + word-based (no LSP) |
| Symbol bar | User-customizable, add/remove/reorder |
| Quick actions | Duplicate/delete/move line, comment toggle, select line |
| External changes | "Reload / keep my edits" prompt when a file changes outside the app |
| Large files | Paged loading, read-only fallback |

</details>

<details>
<summary><b>🌳 Git (JGit, HTTPS + token only)</b></summary>

| Feature | Description |
|---|---|
| Core ops | Clone, status, stage/unstage, commit (+ amend), push/pull/fetch, force push with confirmation |
| Branches | List, create, checkout, delete, rename — including before the first commit (unborn branch) |
| History | Log, commit detail, file history, search by message/author/hash |
| Diff | Inline + side-by-side toggle, image diff preview |
| Merge & cherry-pick | Fast-forward + normal merge, cherry-pick from history |
| Stash & tags | Save/list/apply/pop/drop, tag list/create/delete |
| Guided onboarding | Step-by-step wizard for init → identity → remote → first commit → upstream, skippable |
| Multi-remote | Per-remote token credentials manager |
| Conflicts | Inline accept ours/theirs/both, abort/complete merge |

Not planned: GPG commit signing, submodules, rebase conflict flow.

</details>

<details>
<summary><b>👀 Preview & search</b></summary>

| Feature | Description |
|---|---|
| Preview | Markdown (theme toggle), HTML (split mode, JS off by default), SVG, images — in-editor media tabs |
| File search | Fuzzy filename match, recents, extension/folder filters |
| Code search | Full workspace content search, regex/case/whole word, include/exclude globs, streamed grouped results |
| Search history | Separate history for file vs. code search |

</details>

---

## 🛠️ Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Editor | `io.github.Rosemoe.sora-editor` |
| Git | `org.eclipse.jgit` |
| Async | Coroutines + Flow |
| Storage | DataStore (settings), Room (recent projects) |
| Token security | Android Keystore / `EncryptedSharedPreferences` |
| Lint | [ktlint](https://github.com/pinterest/ktlint) via the [jlleitschuh/ktlint-gradle](https://github.com/JLLeitschuh/ktlint-gradle) plugin, rules in [`.editorconfig`](.editorconfig) |

---

## 📁 Project structure

```
app/
 ├─ core/
 │   ├─ fs/          # file ops, permission helper
 │   ├─ git/         # JGit wrapper (GitRepository, GitAuth, GitOps, ConflictParser)
 │   ├─ editor/      # Sora wrapper, language registry, theme loader
 │   ├─ preview/     # markdown/html/svg/image renderers
 │   ├─ search/      # FileSearchEngine (fuzzy), CodeSearchEngine (grep), SearchHistoryStore
 │   └─ security/    # credentials store (per-remote tokens)
 ├─ feature/
 │   ├─ workspace/   # file tree, recent projects
 │   ├─ search/      # file search overlay, code search screen, history UI
 │   ├─ editor/      # tabs, editor screen, inline conflict bar, preview toggle
 │   ├─ git/         # status, commit, branches, diff, log, stash, remotes
 │   └─ settings/
 ├─ ui/              # theme, common components, navigation
 └─ MainActivity
```

**Pattern**: MVVM + UDF (`UiState` / `UiEvent`), repository layer through JGit.
All JGit calls run on `Dispatchers.IO`, never the main thread.

---

## 🚀 Building

Requires JDK 17.

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # minified/shrunk release APK
./gradlew ktlintCheck        # style check (ktlintFormat to auto-fix)
./gradlew lintDebug          # Android lint
```

Or open the project in Android Studio and run normally.

### CI

`.github/workflows/android-build.yml` runs on every push to `main`, on pull
requests, and on manual dispatch:

- **`lint`** — `ktlintCheck` + `lintDebug`, lint report uploaded as an artifact.
- **`build`** — builds `assembleDebug` and uploads the APK as an artifact.
  If the repo secrets below are set, the debug build is signed with them;
  otherwise it falls back to Gradle's default debug keystore (e.g. on fork PRs).

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

---

## 🗺️ Milestones

- [x] **M1: Skeleton** — project setup, Compose theme, navigation, permission flow
- [ ] **M2: Files** — file tree, CRUD, workspace open *(part 1 done: file tree core; part 2: Open Project sheet, recents, pins)*
- [ ] **M3: Editor core** — Sora embed, tabs, save, TextMate highlighting
- [ ] **M4: Editor extras** — find/replace, symbol bar, autocomplete, settings
- [ ] **M5: Preview** — Markdown, HTML, SVG, Image preview + toggle
- [ ] **M6: Git basics** — credentials store, clone, status, stage, commit, push, pull
- [ ] **M7: Source Control drawer** — drawer UI, changes list, diff viewer
- [ ] **M8: Git advanced** — branches, log, stash, tags, multi-remote
- [ ] **M9: Guided onboarding** — init / identity / remote / first commit wizard
- [ ] **M10: Merge + conflicts** — conflict parser, inline resolution, abort/complete merge
- [ ] **M11: Search** — file search (fuzzy), code search (grep, regex, globs), history
- [ ] **M12: Polish** — perf, large files, error states, dark theme, release CI
- [ ] **M13 (optional)**: Windows 11 Fluent 2 theme

---

## Out of scope

On-device build/run, SDK/Gradle/JDK, terminal, SSH auth, LSP servers, AI agent/MCP,
plugin system, Compose/XML layout/Flutter real preview, rebase conflict flow.
