package com.soligeram.module.service

import android.content.Context
import android.content.SharedPreferences

/**
 * Central feature flag store.
 * Keys are intentionally string constants so the settings UI and hooks
 * share the exact same truth without magic strings scattered everywhere.
 *
 * *Private thought: every toggle here is a small override of Telegram's
 *  intended privacy model — power handed back to the user.*
 */
object PrefManager {

    // ── Feature keys ────────────────────────────────────────────────────
    const val KEY_HIDE_SEEN              = "hide_seen"
    const val KEY_HIDE_SEEN_PRIVATE      = "hide_seen_private"
    const val KEY_HIDE_SEEN_CHANNEL      = "hide_seen_channel"
    const val KEY_HIDE_TYPING            = "hide_typing"
    const val KEY_HIDE_ONLINE            = "hide_online"
    const val KEY_HIDE_STORY_VIEW        = "hide_story_view"
    const val KEY_FAKE_PREMIUM           = "fake_premium"
    const val KEY_SHOW_DELETED_MESSAGES  = "show_deleted_messages"
    const val KEY_SAVE_EDIT_HISTORY      = "save_edit_history"
    const val KEY_MARK_DELETED           = "mark_messages_deleted"
    const val KEY_MARK_EDITED            = "mark_messages_edited"
    const val KEY_ALLOW_SAVE_MEDIA       = "allow_save_media"
    const val KEY_PREVENT_SECRET_DELETE  = "prevent_secret_delete"
    const val KEY_ALLOW_SCREENSHOTS      = "allow_screenshots"
    const val KEY_BOOST_DOWNLOAD         = "boost_download"
    const val KEY_BOOST_DOWNLOAD_LEVEL   = "boost_download_level"   // 0=off 1=on 2=extreme
    const val KEY_HIDE_PROXY_SPONSOR     = "hide_proxy_sponsor"
    const val KEY_HIDE_UPDATE_NOTIF      = "hide_update_notif"
    const val KEY_UNLOCK_CHANNEL_FEAT    = "unlock_channel_features"
    const val KEY_SHOW_USER_ID           = "show_user_id"
    const val KEY_COPY_USER_ID           = "copy_user_id"
    const val KEY_SHOW_CREATION_DATE     = "show_creation_date"

    // ── Download boost levels ────────────────────────────────────────────
    const val BOOST_OFF     = 0
    const val BOOST_ON      = 1
    const val BOOST_EXTREME = 2

    // ── Normal Telegram download constants (bytes) ───────────────────────
    const val CHUNK_DEFAULT  = 524_288    // 512 KB
    const val CHUNK_BOOST    = 2_097_152  // 2 MB
    const val CHUNK_EXTREME  = 4_194_304  // 4 MB
    const val REQ_DEFAULT    = 4
    const val REQ_BOOST      = 8
    const val REQ_EXTREME    = 16

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("soligeram_prefs", Context.MODE_PRIVATE)
    }

    fun isEnabled(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun setEnabled(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
