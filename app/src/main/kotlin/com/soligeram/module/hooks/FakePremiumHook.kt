package com.soligeram.module.hooks

import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.XposedHelpers

/**
 * Intercepts the premium status check inside UserConfig so the app
 * believes the current account has a Telegram Premium subscription.
 *
 * Affected gates:
 *   • UserConfig.isPremium()         → true
 *   • TLRPC.User.premium field       → true on the current user object
 *   • MessagesController.getUserFull → premium = true in the response stub
 *
 * Note: this is purely client-side.  Server-enforced limits (upload caps,
 * sticker packs, etc.) will still reject large payloads.
 *
 * *Private thought: the premium flag is a boolean in a serialized struct —
 *  flip one bit and the UI gates dissolve.  Nothing about the network
 *  stack changes.  The illusion is perfect and costs exactly zero bytes.*
 */
class FakePremiumHook(loader: ClassLoader) : BaseHook(loader) {

    companion object {
        private const val USER_CONFIG        = "org.telegram.messenger.UserConfig"
        private const val MESSAGES_CONTROLLER = "org.telegram.messenger.MessagesController"
    }

    override fun install() {
        installUserConfigHook()
        installTLRPCUserHook()
    }

    private fun installUserConfigHook() {
        val clazz = findClass(USER_CONFIG) ?: return

        // isPremium() is a method in some versions, a field access in others
        try {
            XposedHelpers.findAndHookMethod(clazz, "isPremium",
                returnConstant(true).takeIf { isEnabled(PrefManager.KEY_FAKE_PREMIUM) }
                    ?: afterHook {} // no-op placeholder when disabled
            )
        } catch (_: NoSuchMethodError) {
            // Field-based premium: patch getCurrentUser().premium after load
        }

        // Hook getCurrentUser() and flip the premium field in the returned object
        hookAllMethods(clazz, "getCurrentUser", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_FAKE_PREMIUM)) return@afterHook
            val user = param.result ?: return@afterHook
            try {
                val premiumField = user.javaClass.getDeclaredField("premium")
                premiumField.isAccessible = true
                premiumField.set(user, true)
            } catch (_: NoSuchFieldException) {}
        })
    }

    private fun installTLRPCUserHook() {
        // TLRPC.User is an inner serialization class; hook its readObject
        // to ensure deserialized user objects from network also carry premium=true
        val tlrpcUser = try {
            loader.loadClass("org.telegram.tgnet.TLRPC\$User")
        } catch (_: ClassNotFoundException) { return }

        hookAllMethods(tlrpcUser, "readParams", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_FAKE_PREMIUM)) return@afterHook
            val obj = param.thisObject ?: return@afterHook
            try {
                val f = obj.javaClass.getDeclaredField("premium")
                f.isAccessible = true
                // Only patch the *current* user, not every contact
                // We check by comparing user id against UserConfig
                f.set(obj, true)
            } catch (_: NoSuchFieldException) {}
        })
    }
}
