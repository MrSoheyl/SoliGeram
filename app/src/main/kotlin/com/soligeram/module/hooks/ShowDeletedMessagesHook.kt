package com.soligeram.module.hooks

import com.soligeram.module.core.SoliLog
import com.soligeram.module.data.DeletedMessage
import com.soligeram.module.data.SoliDatabase
import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.XC_MethodHook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intercepts the point where MessagesController processes a
 * TL_updateDeleteMessages or TL_updateDeleteChannelMessages update.
 *
 * Flow:
 *   processUpdateArray() → for each delete update:
 *     1. Read the message objects still in the local cache before deletion
 *     2. Persist them to Room DB as DeletedMessage rows
 *     3. Let the original deletion proceed normally
 *
 * On the UI side, ChatMessageCell.setMessageContent() is hooked to
 * render stored deleted messages with a "🗑 Deleted" label.
 *
 * *Private thought: deletion is a lie once the client has already
 *  received the plaintext.  The TDLib cache still holds the struct.
 *  This hook photocopies it before the shredder runs.*
 */
class ShowDeletedMessagesHook(loader: ClassLoader) : BaseHook(loader) {

    companion object {
        private const val MESSAGES_CONTROLLER = "org.telegram.messenger.MessagesController"
        private const val MESSAGES_STORAGE    = "org.telegram.messenger.MessagesStorage"
        private const val NOTIFICATION_CENTER = "org.telegram.messenger.NotificationCenter"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun install() {
        installDeleteInterceptor()
        installNotificationRemovalSuppressor()
    }

    // ── Intercept the delete update before it wipes the cache ─────────────

    private fun installDeleteInterceptor() {
        val clazz = findClass(MESSAGES_CONTROLLER) ?: return

        // deleteMessages(ArrayList<Integer> messages, long dialogId, ...)
        hookAllMethods(clazz, "deleteMessages", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!isEnabled(PrefManager.KEY_SHOW_DELETED_MESSAGES)) return

                @Suppress("UNCHECKED_CAST")
                val msgIds = param.args.getOrNull(0) as? ArrayList<Int> ?: return
                val dialogId = when (val d = param.args.getOrNull(1)) {
                    is Long -> d
                    is Int  -> d.toLong()
                    else    -> 0L
                }
                if (dialogId == 0L || msgIds.isEmpty()) return

                captureAndPersistMessages(param.thisObject, msgIds, dialogId)
            }
        })

        // Also hook the channel variant: deleteChannelMessages
        hookAllMethods(clazz, "deleteChannelMessages", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!isEnabled(PrefManager.KEY_SHOW_DELETED_MESSAGES)) return
                @Suppress("UNCHECKED_CAST")
                val msgIds   = param.args.getOrNull(0) as? ArrayList<Int> ?: return
                val dialogId = -(param.args.getOrNull(1) as? Long ?: return)
                captureAndPersistMessages(param.thisObject, msgIds, dialogId)
            }
        })
    }

    private fun captureAndPersistMessages(
        controller: Any,
        msgIds: List<Int>,
        dialogId: Long
    ) {
        try {
            // Grab the in-memory dialogMessage map via reflection
            val dialogMessageMap = try {
                val f = controller.javaClass.getDeclaredField("dialogMessage")
                f.isAccessible = true
                f.get(controller)
            } catch (_: Exception) { null }

            // Pull each message object from the map
            val toSave = mutableListOf<DeletedMessage>()
            for (id in msgIds) {
                val msg: Any? = if (dialogMessageMap != null) {
                    try {
                        val getMethod = dialogMessageMap.javaClass
                            .getMethod("get", Any::class.java)
                        getMethod.invoke(dialogMessageMap, id.toLong())
                    } catch (_: Exception) { null }
                } else null

                if (msg != null) {
                    toSave += extractDeletedMessage(msg, dialogId)
                } else {
                    // Minimal row with just the ID so at least we know something was deleted
                    toSave += DeletedMessage(id.toLong(), dialogId, null, System.currentTimeMillis())
                }
            }

            if (toSave.isNotEmpty()) {
                scope.launch {
                    try {
                        val ctx = android.app.AndroidAppHelper.currentApplication()
                        if (ctx != null) {
                            SoliDatabase.get(ctx).deletedMessageDao().insertAll(toSave)
                            SoliLog.d("ShowDeleted", "Saved ${toSave.size} deleted msgs for dialog $dialogId")
                        }
                    } catch (e: Exception) {
                        SoliLog.e("ShowDeleted", "DB persist error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            SoliLog.e("ShowDeleted", "captureAndPersist error: ${e.message}")
        }
    }

    private fun extractDeletedMessage(msgObj: Any, dialogId: Long): DeletedMessage {
        var text: String? = null
        var date: Long = System.currentTimeMillis()
        var msgId: Long = 0L
        var senderId: Long = 0L

        try {
            val msgClass = msgObj.javaClass
            msgId = (msgClass.getDeclaredField("id").also { it.isAccessible = true }.get(msgObj) as? Int)?.toLong() ?: 0L
            date  = (msgClass.getDeclaredField("date").also { it.isAccessible = true }.get(msgObj) as? Int)?.toLong()?.times(1000L) ?: date

            // Try to get message text from the nested TLRPC.Message object
            val messageField = try { msgClass.getDeclaredField("messageOwner").also { it.isAccessible = true } } catch (_: Exception) { null }
            val owner: Any? = messageField?.get(msgObj)
            if (owner != null) {
                text = owner.javaClass.getDeclaredField("message")
                    .also { it.isAccessible = true }.get(owner) as? String
                senderId = try {
                    (owner.javaClass.getDeclaredField("from_id")
                        .also { it.isAccessible = true }.get(owner))?.let { peer ->
                        peer.javaClass.getDeclaredField("user_id")
                            .also { it.isAccessible = true }.get(peer) as? Long
                    } ?: 0L
                } catch (_: Exception) { 0L }
            }
        } catch (e: Exception) {
            SoliLog.w("ShowDeleted", "extractDeletedMessage partial: ${e.message}")
        }

        return DeletedMessage(msgId, dialogId, text, date, senderId)
    }

    // ── Suppress notification removal for deleted messages ─────────────

    private fun installNotificationRemovalSuppressor() {
        val clazz = findClass(MESSAGES_CONTROLLER) ?: return

        hookAllMethods(clazz, "removeDeletedMessagesFromNotifications", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_SHOW_DELETED_MESSAGES)) {
                param.result = null  // don't remove the notification
            }
        })
    }
}
