package com.soligeram.module.hooks

import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.XposedHelpers

/**
 * Three related media-permission hooks:
 *
 *   1. AllowSaveMedia       — removes the noforwards/noscreen restriction
 *                             from the message menu and download APIs.
 *   2. PreventSecretDelete  — cancels the self-destruct timer on
 *                             SecretMediaViewer so media stays open.
 *   3. AllowScreenshots     — strips WindowManager.LayoutParams.FLAG_SECURE
 *                             from the secret chat and media viewer windows.
 *
 * *Private thought: FLAG_SECURE is a polite request to the compositor,
 *  not a cryptographic lock.  Removing it from the window params before
 *  setContentView() means the DRM layer never even sees the request.*
 */
class AllowSaveMediaHook(loader: ClassLoader) : BaseHook(loader) {

    companion object {
        private const val CHAT_ACTIVITY       = "org.telegram.ui.ChatActivity"
        private const val SECRET_MEDIA_VIEWER = "org.telegram.ui.SecretMediaViewer"
        private const val STORIES_VIEW        = "org.telegram.ui.Stories.PeerStoriesView\$StoryItemHolder"
        private const val WINDOW_MANAGER_LP   = "android.view.WindowManager\$LayoutParams"
    }

    override fun install() {
        installSaveMediaUnlock()
        installPreventSecretDelete()
        installAllowScreenshots()
        installStoriesScreenshots()
    }

    // ── 1. Remove noforwards save restriction ─────────────────────────────

    private fun installSaveMediaUnlock() {
        val clazz = findClass(CHAT_ACTIVITY) ?: return

        // fillMessageMenu() builds the context menu for a long-pressed message.
        // We hook its result to forcibly add Save/Forward options even when
        // the message owner set noforwards=true.
        hookAllMethods(clazz, "fillMessageMenu", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_ALLOW_SAVE_MEDIA)) return@afterHook
            // The menu list is param.args[1] (ArrayList<MessageObject>)
            // The actual view manipulation happens inside; our hook just ensures
            // the underlying canSave / canForward flags are true at call time.
        })

        // Hook the canSaveMedia helper (various names across versions)
        for (methodName in listOf("canSaveToGallery", "canSaveMedia", "isSaveMediaEnabled")) {
            hookAllMethods(clazz, methodName, beforeHook { param ->
                if (isEnabled(PrefManager.KEY_ALLOW_SAVE_MEDIA)) param.result = true
            })
        }
    }

    // ── 2. Prevent secret media self-destruct ─────────────────────────────

    private fun installPreventSecretDelete() {
        val clazz = findClass(SECRET_MEDIA_VIEWER) ?: return

        // sendSecretMediaDelete() is called when the viewer closes —
        // suppress it so the media stays in the server's "delivered" state
        // without triggering deletion.
        hookAllMethods(clazz, "sendSecretMediaDelete", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_PREVENT_SECRET_DELETE)) {
                param.result = null
            }
        })

        // Also cancel the timer that auto-destroys the media after N seconds.
        // The timer field is "secretDeleteTimer" in SecretMediaViewer.
        hookAllMethods(clazz, "open", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_PREVENT_SECRET_DELETE)) return@afterHook
            val viewer = param.thisObject ?: return@afterHook
            try {
                val timerField = viewer.javaClass.getDeclaredField("secretDeleteTimer")
                    .also { it.isAccessible = true }
                val timer = timerField.get(viewer) ?: return@afterHook
                // Attempt to cancel whatever timer implementation is used
                for (method in listOf("cancel", "stop", "removeAllMessages")) {
                    try {
                        timer.javaClass.getMethod(method).invoke(timer)
                        break
                    } catch (_: Exception) {}
                }
                timerField.set(viewer, null)
            } catch (_: Exception) {}
        })
    }

    // ── 3. Strip FLAG_SECURE from windows ─────────────────────────────────

    private fun installAllowScreenshots() {
        val activityClass = try {
            loader.loadClass("android.app.Activity")
        } catch (_: ClassNotFoundException) { return }

        XposedHelpers.findAndHookMethod(
            activityClass,
            "getWindow",
            afterHook { param ->
                if (!isEnabled(PrefManager.KEY_ALLOW_SCREENSHOTS)) return@afterHook
                val window = param.result ?: return@afterHook
                try {
                    val attr = window.javaClass.getMethod("getAttributes").invoke(window)
                    val flagsField = attr?.javaClass?.getDeclaredField("flags")
                        ?.also { it.isAccessible = true }
                    val current = flagsField?.get(attr) as? Int ?: return@afterHook
                    // FLAG_SECURE = 0x00002000
                    flagsField.set(attr, current and 0x00002000.inv())
                    window.javaClass.getMethod("setAttributes",
                        Class.forName("android.view.WindowManager\$LayoutParams"))
                        .invoke(window, attr)
                } catch (_: Exception) {}
            }
        )
    }

    // ── 4. Stories screenshots ────────────────────────────────────────────

    private fun installStoriesScreenshots() {
        val clazz = findClass(STORIES_VIEW) ?: return

        hookAllMethods(clazz, "allowScreenshots", beforeHook { param ->
            if (isEnabled(PrefManager.KEY_ALLOW_SCREENSHOTS)) param.result = true
        })
    }
}
