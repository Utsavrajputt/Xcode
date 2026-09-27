package com.invictus.xcode.core.permission

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

/**
 * Whether the startup permission onboarding (storage / notifications / battery) has
 * been finished or skipped once. Separate from [com.invictus.xcode.core.git.GitOnboardingPrefs],
 * which is the per-repo "M9 guided Git onboarding" wizard -- this one is app-level and
 * runs once, before Home.
 */
class OnboardingPrefs(private val context: Context) {
    private object Keys {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val completed: Flow<Boolean> = context.onboardingDataStore.data
        .map { it[Keys.COMPLETED] ?: false }

    suspend fun isCompleted(): Boolean = context.onboardingDataStore.data.first()[Keys.COMPLETED] ?: false

    suspend fun setCompleted(completed: Boolean) {
        context.onboardingDataStore.edit { it[Keys.COMPLETED] = completed }
    }
}
