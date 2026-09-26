package com.invictus.xcode.di

import android.content.Context
import com.invictus.xcode.core.editor.EditorSessionStore
import com.invictus.xcode.core.editor.EditorSettingsStore
import com.invictus.xcode.core.editor.TextMateSupport
import com.invictus.xcode.core.fs.FileOpenPolicy
import com.invictus.xcode.core.fs.FileOps
import com.invictus.xcode.core.data.AppDatabase
import com.invictus.xcode.core.fs.StoragePermission
import com.invictus.xcode.core.project.ProjectBackup
import com.invictus.xcode.core.project.ProjectRepository
import com.invictus.xcode.core.security.GitCredentialStore
import java.io.File

/**
 * Manual dependency container (no Hilt/Koin, same approach as xmd).
 * Add each new app-wide dependency here as a `val` (use `by lazy` for heavy ones);
 * ViewModels pull what they need from it through their Factory.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val storagePermission: StoragePermission by lazy { StoragePermission(appContext) }

    val fileOps: FileOps by lazy { FileOps() }

    val fileOpenPolicy: FileOpenPolicy by lazy { FileOpenPolicy() }

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val projectRepository: ProjectRepository by lazy { ProjectRepository(database) }

    val projectBackup: ProjectBackup by lazy { ProjectBackup() }

    val textMate: TextMateSupport by lazy { TextMateSupport(appContext) }

    val editorSessionStore: EditorSessionStore by lazy { EditorSessionStore(appContext) }

    val editorSettingsStore: EditorSettingsStore by lazy { EditorSettingsStore(appContext) }

    val gitCredentialStore: GitCredentialStore by lazy { GitCredentialStore(appContext) }

    /** App-level (global) git identity file holding user.name / user.email. */
    val gitGlobalIdentityFile: File by lazy { File(appContext.filesDir, "git_identity") }
}
