package com.aniket.tapshare.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small key-value settings. SharedPreferences is enough here (no extra dependency). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("tapshare_settings", Context.MODE_PRIVATE)

    private val _sharingEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHARING_ENABLED, true))
    val sharingEnabled: StateFlow<Boolean> = _sharingEnabled.asStateFlow()

    fun setSharingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHARING_ENABLED, enabled) }
        _sharingEnabled.value = enabled
    }

    /** Needed to tell "never asked" apart from "denied for good" (Android has no direct API). */
    fun wasPermissionAsked(permission: String): Boolean =
        prefs.getStringSet(KEY_ASKED_PERMISSIONS, emptySet())!!.contains(permission)

    fun markPermissionsAsked(permissions: Collection<String>) {
        val updated = prefs.getStringSet(KEY_ASKED_PERMISSIONS, emptySet())!! + permissions
        prefs.edit { putStringSet(KEY_ASKED_PERMISSIONS, updated) }
    }

    private companion object {
        const val KEY_SHARING_ENABLED = "sharing_enabled"
        const val KEY_ASKED_PERMISSIONS = "asked_permissions"
    }
}
