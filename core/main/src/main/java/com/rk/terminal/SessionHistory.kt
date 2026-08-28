package com.rk.terminal

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.utils.application
import java.lang.ref.WeakReference

data class SessionMetadata(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val workingDir: String = "",
    val isSandbox: Boolean = true,
)

object SessionHistory {
    private const val PREFS_NAME = "session_history"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_HISTORY_SIZE = 100

    private val gson = Gson()
    private var prefs: WeakReference<SharedPreferences?> = WeakReference(null)

    private fun getPrefs(): SharedPreferences {
        return prefs.get() ?: run {
            val p = application!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = WeakReference(p)
            p
        }
    }

    fun saveSession(metadata: SessionMetadata) {
        val sessions = getAllSessions().toMutableList()
        
        // Remove existing session with same ID if exists
        sessions.removeAll { it.id == metadata.id }
        
        // Add updated session at the beginning
        sessions.add(0, metadata.copy(lastUsedAt = System.currentTimeMillis()))
        
        // Trim to max size
        val trimmedSessions = if (sessions.size > MAX_HISTORY_SIZE) {
            sessions.take(MAX_HISTORY_SIZE)
        } else {
            sessions
        }
        
        val json = gson.toJson(trimmedSessions)
        getPrefs().edit().putString(KEY_SESSIONS, json).apply()
    }

    fun getAllSessions(): List<SessionMetadata> {
        val json = getPrefs().getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SessionMetadata>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getSession(id: String): SessionMetadata? {
        return getAllSessions().find { it.id == id }
    }

    fun updateSessionLastUsed(id: String) {
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = sessions[index].copy(lastUsedAt = System.currentTimeMillis())
            sessions.removeAt(index)
            sessions.add(0, updated)
            
            val json = gson.toJson(sessions)
            getPrefs().edit().putString(KEY_SESSIONS, json).apply()
        }
    }

    fun deleteSession(id: String) {
        val sessions = getAllSessions().toMutableList()
        sessions.removeAll { it.id == id }
        
        val json = gson.toJson(sessions)
        getPrefs().edit().putString(KEY_SESSIONS, json).apply()
    }

    fun deleteAllSessions() {
        getPrefs().edit().remove(KEY_SESSIONS).apply()
    }

    fun searchSessions(query: String): List<SessionMetadata> {
        if (query.isBlank()) return getAllSessions()
        
        val lowercaseQuery = query.lowercase()
        return getAllSessions().filter { session ->
            session.name.lowercase().contains(lowercaseQuery) ||
            session.workingDir.lowercase().contains(lowercaseQuery)
        }
    }

    fun getRecentSessions(limit: Int = 10): List<SessionMetadata> {
        return getAllSessions()
            .sortedByDescending { it.lastUsedAt }
            .take(limit)
    }
}
