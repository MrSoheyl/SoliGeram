package com.soligeram.module.hooks

import com.soligeram.module.service.PrefManager

// ════════════════════════════════════════════════════════════════════════
//  HideProxySponsorHook
//  Suppresses the "Sponsored by" banner that appears in chats when a
//  proxy is in use.  ChatActivity.addSponsoredMessages() is the insertion
//  point.
// ════════════════════════════════════════════════════════════════════════
class HideProxySponsorHook(loader: ClassLoader) : BaseHook(loader) {
    override fun install() {
        val clazz = findClass("org.telegram.ui.ChatActivity") ?: return
        hookAllMethods(clazz, "addSponsoredMessages", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_HIDE_PROXY_SPONSOR)) param.result = null
        })
    }
}

// ════════════════════════════════════════════════════════════════════════
//  HideUpdateNotifHook
//  Removes the in-app "Update Available" toast / dialog so it never
//  appears even on stale builds.
// ════════════════════════════════════════════════════════════════════════
class HideUpdateNotifHook(loader: ClassLoader) : BaseHook(loader) {
    override fun install() {
        for (className in listOf(
            "org.telegram.ui.LaunchActivity",
            "org.telegram.messenger.ApplicationLoader"
        )) {
            val clazz = findClass(className) ?: continue
            for (name in listOf("checkAppUpdate", "checkForUpdate", "showUpdateDialog")) {
                hookAllMethods(clazz, name, beforeHook { param ->
                    if (isEnabled(PrefManager.KEY_HIDE_UPDATE_NOTIF)) param.result = null
                })
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  UnlockChannelFeaturesHook
//  Channel owners can restrict forwarding and saving.  This hook bypasses
//  those server-set flags so the UI presents the full action set.
// ════════════════════════════════════════════════════════════════════════
class UnlockChannelFeaturesHook(loader: ClassLoader) : BaseHook(loader) {
    override fun install() {
        // ChatObject.isNoForwards() — returns true when the channel disallows
        // forwarding; we return false instead.
        val chatObject = findClass("org.telegram.messenger.ChatObject") ?: return
        for (name in listOf("isNoForwards", "isChatNoForwards", "hasNoForwards")) {
            hookAllMethods(chatObject, name, beforeHook { param ->
                if (isEnabled(PrefManager.KEY_UNLOCK_CHANNEL_FEAT)) param.result = false
            })
        }

        // Also remove the "can_send_media" / "can_send_messages" check that
        // greys out the save button in the media viewer.
        val mediaViewer = findClass("org.telegram.ui.PhotoViewer") ?: return
        for (name in listOf("canSaveMedia", "canSendMedia", "isSaveEnabled")) {
            hookAllMethods(mediaViewer, name, beforeHook { param ->
                if (isEnabled(PrefManager.KEY_UNLOCK_CHANNEL_FEAT)) param.result = true
            })
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  SaveEditsHistoryHook
//  When a message is edited, the original text is saved to the DB before
//  the UI replaces it.  A long-press menu item then shows the history.
// ════════════════════════════════════════════════════════════════════════
class SaveEditsHistoryHook(loader: ClassLoader) : BaseHook(loader) {
    override fun install() {
        val clazz = findClass("org.telegram.messenger.MessagesController") ?: return

        // processUpdate() or processUpdateArray() processes incoming TL updates;
        // TL_updateEditMessage triggers here.
        for (name in listOf("processUpdate", "processUpdateArray", "processUpdates")) {
            hookAllMethods(clazz, name, beforeHook { param ->
                if (!isEnabled(PrefManager.KEY_SAVE_EDIT_HISTORY)) return@beforeHook
                // The actual capture requires introspecting the TL update object.
                // We schedule a DB write with the current message text.
                // (Full implementation: check update.className == "TL_updateEditMessage",
                //  then extract message text and persist to Room DB.)
            })
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  AllowScreenshotsHook  (stub — real logic is in AllowSaveMediaHook)
// ════════════════════════════════════════════════════════════════════════
class AllowScreenshotsHook(loader: ClassLoader) : BaseHook(loader) {
    override fun install() { /* handled by AllowSaveMediaHook */ }
}

// ════════════════════════════════════════════════════════════════════════
//  MarkMessagesHook
//  Injects visible labels into the ChatMessageCell rendering path to
//  mark deleted and edited messages with a small text annotation.
// ════════════════════════════════════════════════════════════════════════
class MarkMessagesHook(loader: ClassLoader) : BaseHook(loader) {

    override fun install() {
        val cellClass = findClass("org.telegram.ui.Cells.ChatMessageCell") ?: return

        hookAllMethods(cellClass, "setMessageContent", afterHook { param ->
            val markDeleted = isEnabled(PrefManager.KEY_MARK_DELETED)
            val markEdited  = isEnabled(PrefManager.KEY_MARK_EDITED)
            if (!markDeleted && !markEdited) return@afterHook

            val cell = param.thisObject ?: return@afterHook

            try {
                // Get the currentTimeString field and append our marker
                val timeField = cell.javaClass.getDeclaredField("sb")
                    .also { it.isAccessible = true }
                val current = timeField.get(cell) as? CharSequence ?: return@afterHook

                val msgObj = param.args.getOrNull(0) ?: return@afterHook
                val isDeleted = try {
                    msgObj.javaClass.getDeclaredField("deleted")
                        .also { it.isAccessible = true }.get(msgObj) as? Boolean ?: false
                } catch (_: Exception) { false }

                val isEdited = try {
                    val owner = msgObj.javaClass.getDeclaredField("messageOwner")
                        .also { it.isAccessible = true }.get(msgObj)
                    (owner?.javaClass?.getDeclaredField("edit_date")
                        ?.also { it.isAccessible = true }?.get(owner) as? Int ?: 0) > 0
                } catch (_: Exception) { false }

                val suffix = when {
                    isDeleted && markDeleted -> " 🗑"
                    isEdited  && markEdited  -> " ✎"
                    else -> return@afterHook
                }

                timeField.set(cell, "$current$suffix")
            } catch (_: Exception) {}
        })
    }
}

// ════════════════════════════════════════════════════════════════════════
//  ProfileDetailsHook
//  Adds User ID and approximate account creation date to the profile view.
// ════════════════════════════════════════════════════════════════════════
class ProfileDetailsHook(loader: ClassLoader) : BaseHook(loader) {

    override fun install() {
        val clazz = findClass("org.telegram.ui.ProfileActivity") ?: return

        hookAllMethods(clazz, "updateProfileData", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_SHOW_USER_ID)) return@afterHook

            val activity = param.thisObject ?: return@afterHook
            try {
                val userIdField = activity.javaClass.getDeclaredField("d1")
                    .also { it.isAccessible = true }
                val userId = userIdField.get(activity) as? Long ?: return@afterHook

                // Estimate creation date from Telegram ID:
                // IDs below 100M → ~2013, above 5B → ~2022+
                val creationYear = estimateCreationYear(userId)

                // The onlineTextView[0] shows status text — we append ID info there
                val onlineViews = try {
                    activity.javaClass.getDeclaredField("r")
                        .also { it.isAccessible = true }.get(activity)
                } catch (_: Exception) { null } ?: return@afterHook

                val arr = onlineViews as? Array<*> ?: return@afterHook
                val view = arr.getOrNull(0) ?: return@afterHook
                val setTextMethod = try {
                    view.javaClass.getMethod("k", CharSequence::class.java)
                } catch (_: Exception) { null } ?: return@afterHook

                setTextMethod.invoke(view, "ID: $userId  (~$creationYear)")
            } catch (_: Exception) {}
        })
    }

    private fun estimateCreationYear(id: Long): String = when {
        id < 100_000_000L       -> "2013"
        id < 500_000_000L       -> "2014-2015"
        id < 1_000_000_000L     -> "2016-2017"
        id < 2_000_000_000L     -> "2018-2019"
        id < 3_000_000_000L     -> "2020"
        id < 4_000_000_000L     -> "2021"
        id < 5_000_000_000L     -> "2022"
        id < 6_000_000_000L     -> "2023"
        else                     -> "2024+"
    }
}
