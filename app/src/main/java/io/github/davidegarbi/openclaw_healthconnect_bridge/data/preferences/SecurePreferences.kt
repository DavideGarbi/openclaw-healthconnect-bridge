package io.github.davidegarbi.openclaw_healthconnect_bridge.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePreferences(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var bearerToken: String?
        get() = prefs.getString(KEY_BEARER_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_BEARER_TOKEN, value).apply()

    companion object {
        private const val KEY_BEARER_TOKEN = "bearer_token"
    }
}
