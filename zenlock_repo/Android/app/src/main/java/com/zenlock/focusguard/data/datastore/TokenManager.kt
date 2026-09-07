package com.zenlock.focusguard.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {
    companion object {
        private val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val IS_PROFILE_COMPLETE_KEY = booleanPreferencesKey("is_profile_complete")
    }

    val jwtToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[JWT_TOKEN_KEY]
    }
    
    val isProfileComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_PROFILE_COMPLETE_KEY] ?: false
    }

    suspend fun saveToken(token: String, email: String, isProfileComplete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[JWT_TOKEN_KEY] = token
            preferences[USER_EMAIL_KEY] = email
            preferences[IS_PROFILE_COMPLETE_KEY] = isProfileComplete
        }
    }

    suspend fun completeProfile() {
        context.dataStore.edit { preferences ->
            preferences[IS_PROFILE_COMPLETE_KEY] = true
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(JWT_TOKEN_KEY)
            preferences.remove(USER_EMAIL_KEY)
            preferences.remove(IS_PROFILE_COMPLETE_KEY)
        }
    }
}
