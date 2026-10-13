package com.soligeram.module

import com.soligeram.module.core.ObfuscationMapper
import com.soligeram.module.core.SoliLog
import com.soligeram.module.hooks.*
import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entry point.  Resolves variant → loads obfuscation mappings →
 * installs every feature hook in order.
 *
 * *Private thought: every hook list entry below is a knife in a drawer —
 *  quiet until called, but the drawer is always open.*
 */
class MainHook : IXposedHookLoadPackage {

    private val TAG = "MainHook"

    private val supportedPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "tw.nekomimi.nekogram",
        "it.octogram.android",
        "com.exteragram.messenger"
    )

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in supportedPackages) return
        SoliLog.i(TAG, "Attaching to: ${lpparam.packageName}")

        val loader  = lpparam.classLoader
        val variant = detectVariant(lpparam.packageName)

        try {
            val ctx = android.app.AndroidAppHelper.currentApplication()
            if (ctx != null) {
                PrefManager.init(ctx)
                val moduleCtx = ctx.createPackageContext(
                    "com.soligeram.module",
                    android.content.Context.CONTEXT_IGNORE_SECURITY
                )
                ObfuscationMapper.load(moduleCtx.assets, variant)
                SoliLog.i(TAG, "Bootstrap OK — variant=$variant")
            }
        } catch (e: Exception) {
            SoliLog.e(TAG, "Bootstrap error: ${e.message}")
        }

        installHooks(loader)
    }

    private fun detectVariant(pkg: String): String = when (pkg) {
        "tw.nekomimi.nekogram"     -> "nekogram"
        "it.octogram.android"      -> "octogram"
        "com.exteragram.messenger" -> "cherrygram"
        else                       -> "telegram"
    }

    private fun installHooks(loader: ClassLoader) {
        val hooks: List<BaseHook> = listOf(
            GhostModeHook(loader),
            FakePremiumHook(loader),
            ShowDeletedMessagesHook(loader),
            SaveEditsHistoryHook(loader),
            MarkMessagesHook(loader),
            AllowSaveMediaHook(loader),
            PreventSecretMediaDeletionHook(loader),
            BoostDownloadHook(loader),
            HideProxySponsorHook(loader),
            UnlockChannelFeaturesHook(loader),
            HideUpdateNotifHook(loader),
            ProfileDetailsHook(loader),
            CopyUserIdHook(loader),
            ScrollToTopHook(loader),
            OfflineLabelHook(loader),
            AllowScreenshotsHook(loader)
        )

        var ok = 0; var fail = 0
        for (hook in hooks) {
            try {
                hook.install()
                SoliLog.i(TAG, "✓ ${hook.javaClass.simpleName}")
                ok++
            } catch (e: Throwable) {
                SoliLog.e(TAG, "✗ ${hook.javaClass.simpleName}: ${e.message}")
                fail++
            }
        }
        SoliLog.i(TAG, "Hooks: $ok OK, $fail FAILED")
    }
}
