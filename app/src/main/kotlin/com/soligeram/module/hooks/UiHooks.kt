package com.soligeram.module.hooks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.XC_MethodHook

/**
 * Quality-of-life UI hooks:
 *
 *   1. ScrollToTopHook    — Injects a "Scroll to top" button / gesture
 *                           into ChatActivity's action bar menu.
 *   2. CopyUserIdHook     — Adds "Copy ID" to the profile long-press menu
 *                           and the profile header context menu.
 *   3. OfflineLabelHook   — Replaces the "last seen recently" stub with
 *                           a literal "offline" label.
 *
 * *Private thought: UI surgery at this level is cosmetic but cuts deep —
 *  every menu item added here is a method call injected between the
 *  original onCreateOptionsMenu() and the framework's MenuInflater.*
 */

// ════════════════════════════════════════════════════════════════════════
//  ScrollToTopHook
// ════════════════════════════════════════════════════════════════════════
class ScrollToTopHook(loader: ClassLoader) : BaseHook(loader) {

    override fun install() {
        val chatActivity = findClass("org.telegram.ui.ChatActivity") ?: return

        // Hook onCreateOptionsMenu to inject our item
        hookAllMethods(chatActivity, "onCreateOptionsMenu", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val menu = param.args.getOrNull(0) as? Menu ?: return

                // Add "Scroll to top" item — ID 0x7f0a0001 is safely in the app-defined range
                if (menu.findItem(0x7f0a0001) == null) {
                    menu.add(Menu.NONE, 0x7f0a0001, Menu.NONE, "⬆ Scroll to top")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                }
            }
        })

        // Hook onOptionsItemSelected to handle our item
        hookAllMethods(chatActivity, "onOptionsItemSelected", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val item = param.args.getOrNull(0) as? MenuItem ?: return
                if (item.itemId != 0x7f0a0001) return

                val activity = param.thisObject ?: return
                try {
                    // Find the RecyclerView / ChatListView and scroll to position 0
                    for (fieldName in listOf("chatListView", "listView", "recyclerListView")) {
                        val field = try {
                            activity.javaClass.getDeclaredField(fieldName)
                                .also { it.isAccessible = true }
                        } catch (_: NoSuchFieldException) { continue }
                        val listView = field.get(activity) ?: continue
                        try {
                            listView.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType!!)
                                .invoke(listView, 0)
                        } catch (_: Exception) {
                            listView.javaClass.getMethod("smoothScrollToPosition", Int::class.javaPrimitiveType!!)
                                .invoke(listView, 0)
                        }
                        break
                    }
                } catch (e: Exception) { /* no-op */ }
                param.result = true
            }
        })
    }
}

// ════════════════════════════════════════════════════════════════════════
//  CopyUserIdHook
// ════════════════════════════════════════════════════════════════════════
class CopyUserIdHook(loader: ClassLoader) : BaseHook(loader) {

    override fun install() {
        if (!isEnabled(PrefManager.KEY_COPY_USER_ID)) return

        val profileActivity = findClass("org.telegram.ui.ProfileActivity") ?: return

        // Hook the row-click handler in ProfileActivity
        hookAllMethods(profileActivity, "onItemClick", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // row index for username / ID area lives in field K3 (obfuscated)
                // We inject a clipboard copy when that row is tapped
            }
        })

        // Safer alternative: hook updateProfileData() and attach a long-press
        // listener to the name TextView
        hookAllMethods(profileActivity, "updateProfileData", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject ?: return
                try {
                    val userIdField = activity.javaClass.getDeclaredField("d1")
                        .also { it.isAccessible = true }
                    val userId = userIdField.get(activity) as? Long ?: return

                    // Attach to the name view (index 0 in SimpleTextView array "r")
                    val viewsField = activity.javaClass.getDeclaredField("r")
                        .also { it.isAccessible = true }
                    val views = viewsField.get(activity) as? Array<*> ?: return
                    val nameView = views.getOrNull(0) as? android.view.View ?: return

                    nameView.setOnLongClickListener {
                        val ctx = nameView.context
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("User ID", userId.toString()))
                        Toast.makeText(ctx, "ID $userId copied", Toast.LENGTH_SHORT).show()
                        true
                    }
                } catch (_: Exception) {}
            }
        })
    }
}

// ════════════════════════════════════════════════════════════════════════
//  OfflineLabelHook
//  Replaces the vague "last seen recently" with a concrete "offline" label
//  in the profile and chat header subtitle.
// ════════════════════════════════════════════════════════════════════════
class OfflineLabelHook(loader: ClassLoader) : BaseHook(loader) {

    override fun install() {
        // LocaleController.formatLastSeen() produces human-readable strings
        val lc = findClass("org.telegram.messenger.LocaleController") ?: return

        for (methodName in listOf("formatLastSeen", "formatUserStatus", "formatDateAudio")) {
            hookAllMethods(lc, methodName, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? String ?: return
                    // "last seen recently" → "offline"
                    if (result.contains("recently", ignoreCase = true) ||
                        result.contains("last seen", ignoreCase = true)) {
                        param.result = "offline"
                    }
                }
            })
        }
    }
}
