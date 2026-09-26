package com.invictus.xcode.core.git

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gitOnboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "git_onboarding")

/**
 * M9 guided-onboarding persistence: a global on/off switch plus the set of project
 * paths the user finished or dismissed, so the wizard only appears when a repo is
 * genuinely incomplete (plan 3.4 "guided Git onboarding").
 */
class GitOnboardingPrefs(private val context: Context) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("git_onboarding_enabled")
        val DONE_PROJECTS = stringSetPreferencesKey("git_onboarding_done_projects")
    }

    /** Global switch; off by default -- the wizard no longer auto-triggers unless the user opts in. */
    val enabled: Flow<Boolean> = context.gitOnboardingDataStore.data
        .map { it[Keys.ENABLED] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        context.gitOnboardingDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun isDone(projectPath: String): Boolean =
        context.gitOnboardingDataStore.data.first()[Keys.DONE_PROJECTS]?.contains(projectPath) == true

    suspend fun markDone(projectPath: String) {
        context.gitOnboardingDataStore.edit { prefs ->
            val set = prefs[Keys.DONE_PROJECTS] ?: emptySet()
            if (projectPath !in set) prefs[Keys.DONE_PROJECTS] = set + projectPath
        }
    }
}
