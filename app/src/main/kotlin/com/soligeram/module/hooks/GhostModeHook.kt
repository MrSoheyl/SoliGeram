package com.soligeram.module.hooks

import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Ghost Mode — four separate privacy interceptors bundled together because
 * they all serve the same purpose: make you invisible.
 *
 * Hooks:
 *   MessagesController.markDialogAsRead()    → HideSeen
 *   MessagesController.sendTyping()          → HideTyping
 *   ConnectionsManager.updateDcSettings()    → HideOnline (partial)
 *   StoriesController.markStoryAsRead()      → HideStoryView
 *
 * *Private thought: presence is a social contract.  These hooks
 *  let the user opt out silently — no read receipt, no typing bubble,
 *  no "online" timestamp leaking in the server's logs.*
 */
class GhostModeHook(loader: ClassLoader) : BaseHook(loader) {

    companion object {
        private const val MESSAGES_CONTROLLER   = "org.telegram.messenger.MessagesController"
        private const val CONNECTIONS_MANAGER   = "org.telegram.messenger.ConnectionsManager"
        private const val STORIES_CONTROLLER    = "org.telegram.ui.Stories.StoriesController"
        private const val CHAT_ACTIVITY         = "org.telegram.ui.ChatActivity"
    }

    override fun install() {
        installHideSeen()
        installHideTyping()
        installHideOnline()
        installHideStoryView()
    }

    // ── 1. Hide Seen ─────────────────────────────────────────────────────

    private fun installHideSeen() {
        val clazz = findClass(MESSAGES_CONTROLLER) ?: return

        // markDialogAsRead(long dialogId, int maxDate) — suppress read receipts
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                "markDialogAsRead",
                Long::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                beforeHook { param ->
                    if (!isEnabled(PrefManager.KEY_HIDE_SEEN)) return@beforeHook
                    // Check whether the dialog is a private chat or channel
                    val dialogId = param.args[0] as Long
                    val isChannel = dialogId < 0 && isEnabled(PrefManager.KEY_HIDE_SEEN_CHANNEL)
                    val isPrivate = dialogId > 0 && isEnabled(PrefManager.KEY_HIDE_SEEN_PRIVATE)
                    if (isChannel || isPrivate) {
                        param.result = null  // suppress the call entirely
                    }
                }
            )
        } catch (e: NoSuchMethodError) {
            // Telegram 12.x renamed this; try the int-only variant
            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "markDialogAsRead",
                    Long::class.javaPrimitiveType!!,
                    beforeHook { param ->
                        if (isEnabled(PrefManager.KEY_HIDE_SEEN)) param.result = null
                    }
                )
            } catch (e2: NoSuchMethodError) {
                // Also hook sendReadAck which is the network call
                hookAllMethods(clazz, "sendReadAck", beforeHook { param ->
                    if (isEnabled(PrefManager.KEY_HIDE_SEEN)) param.result = null
                })
            }
        }

        // Also hook ChatActivity.sendSecretMessageRead to cover secret chats
        val chatActivity = findClass(CHAT_ACTIVITY) ?: return
        hookMethod(
            chatActivity,
            "sendSecretMessageRead",
            beforeHook { param ->
                if (isEnabled(PrefManager.KEY_HIDE_SEEN)) param.result = null
            }
        )
    }

    // ── 2. Hide Typing ───────────────────────────────────────────────────

    private fun installHideTyping() {
        val clazz = findClass(MESSAGES_CONTROLLER) ?: return

        // sendTyping(long dialogId, TLRPC.InputPeer peer, int action, int topMsgId)
        hookAllMethods(clazz, "sendTyping", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_HIDE_TYPING)) param.result = null
        })
    }

    // ── 3. Hide Online ───────────────────────────────────────────────────

    private fun installHideOnline() {
        val clazz = findClass(CONNECTIONS_MANAGER) ?: return

        // updateDcSettings() triggers an online presence ping — suppress it
        hookAllMethods(clazz, "updateDcSettings", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isEnabled(PrefManager.KEY_HIDE_ONLINE)) param.result = null
            }
        })

        // Also hook sendRequest for account.updateStatus (TLRPC.TL_account_updateStatus)
        // This is best-effort — the exact TL class name varies by build
        val messagesController = findClass(MESSAGES_CONTROLLER) ?: return
        hookAllMethods(messagesController, "checkIfUpdatesStarted", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_HIDE_ONLINE)) param.result = null
        })
    }

    // ── 4. Hide Story View ───────────────────────────────────────────────

    private fun installHideStoryView() {
        val clazz = findClass(STORIES_CONTROLLER) ?: return

        // markStoryAsRead(PeerStories, StoryItem, boolean) → return false silently
        hookAllMethods(clazz, "markStoryAsRead", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_HIDE_STORY_VIEW)) {
                param.result = false
            }
        })
    }
}
