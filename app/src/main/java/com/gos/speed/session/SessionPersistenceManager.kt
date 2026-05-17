package com.gos.speed.session

import android.content.Context
import android.util.Base64
import com.gos.speed.data.UwbRole

data class SavedSession(
    val code: String,
    val role: UwbRole,
    val sessionId: Int,
    val sessionKey: ByteArray,
    val channel: Int,
    val preambleIndex: Int
)

class SessionPersistenceManager(context: Context) {

    private val prefs = context.getSharedPreferences("uwb_session", Context.MODE_PRIVATE)

    fun save(session: SavedSession) {
        prefs.edit()
            .putString("code", session.code)
            .putString("role", session.role.name)
            .putInt("session_id", session.sessionId)
            .putString("session_key", Base64.encodeToString(session.sessionKey, Base64.NO_WRAP))
            .putInt("channel", session.channel)
            .putInt("preamble_index", session.preambleIndex)
            .apply()
    }

    fun load(): SavedSession? {
        val code = prefs.getString("code", null) ?: return null
        val roleName = prefs.getString("role", null) ?: return null
        val role = runCatching { UwbRole.valueOf(roleName) }.getOrNull() ?: return null
        if (role == UwbRole.NONE) return null
        val keyStr = prefs.getString("session_key", null) ?: return null
        return SavedSession(
            code = code,
            role = role,
            sessionId = prefs.getInt("session_id", 0),
            sessionKey = Base64.decode(keyStr, Base64.NO_WRAP),
            channel = prefs.getInt("channel", 0),
            preambleIndex = prefs.getInt("preamble_index", 0)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
