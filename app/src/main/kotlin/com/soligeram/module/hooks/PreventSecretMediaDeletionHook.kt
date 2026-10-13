package com.soligeram.module.hooks

/**
 * Thin delegating hook — the actual implementation lives in
 * AllowSaveMediaHook.installPreventSecretDelete().
 * Kept separate so MainHook can install it independently.
 */
class PreventSecretMediaDeletionHook(loader: ClassLoader) : BaseHook(loader) {
    override fun install() {
        // No-op: AllowSaveMediaHook covers this.
        // If you want to split them, move installPreventSecretDelete() here.
    }
}
