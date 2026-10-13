package com.soligeram.module.hooks

import com.soligeram.module.service.PrefManager

/**
 * Patches FileLoadOperation and DownloadController to use larger chunk
 * sizes and a higher concurrent-request ceiling.
 *
 * Default Telegram values:
 *   downloadChunkSizeBig  = 524 288  (512 KB)
 *   maxDownloadRequests   = 4
 *   maxDownloadRequestsBig = 4
 *
 * Boost level 1 (ON):
 *   chunk = 2 097 152 (2 MB), requests = 8
 *
 * Boost level 2 (EXTREME):
 *   chunk = 4 194 304 (4 MB), requests = 16
 *   ⚠  May hit Telegram server rate limits.
 *
 * *Private thought: the wire doesn't care about Telegram's self-imposed
 *  throttle.  We are merely configuring the client-side scheduler to ask
 *  for more bytes per round-trip — every request is still TLS, still MTProto.*
 */
class BoostDownloadHook(loader: ClassLoader) : BaseHook(loader) {

    companion object {
        private const val FILE_LOADER          = "org.telegram.messenger.FileLoader"
        private const val DOWNLOAD_CONTROLLER  = "org.telegram.messenger.DownloadController"
        private const val FILE_LOAD_OPERATION  = "org.telegram.messenger.FileLoadOperation"
    }

    override fun install() {
        patchFileLoader()
        patchDownloadController()
    }

    private fun currentChunkSize(): Int = when (PrefManager.getInt(PrefManager.KEY_BOOST_DOWNLOAD_LEVEL)) {
        PrefManager.BOOST_ON      -> PrefManager.CHUNK_BOOST
        PrefManager.BOOST_EXTREME -> PrefManager.CHUNK_EXTREME
        else                      -> PrefManager.CHUNK_DEFAULT
    }

    private fun currentMaxRequests(): Int = when (PrefManager.getInt(PrefManager.KEY_BOOST_DOWNLOAD_LEVEL)) {
        PrefManager.BOOST_ON      -> PrefManager.REQ_BOOST
        PrefManager.BOOST_EXTREME -> PrefManager.REQ_EXTREME
        else                      -> PrefManager.REQ_DEFAULT
    }

    private fun patchFileLoader() {
        val clazz = findClass(FILE_LOADER) ?: return

        // Hook getInstance() and patch static fields immediately after the object is created
        hookAllMethods(clazz, "getInstance", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_BOOST_DOWNLOAD)) return@afterHook
            val instance = param.result ?: return@afterHook
            patchFieldIfPresent(instance, "downloadChunkSizeBig",    currentChunkSize())
            patchFieldIfPresent(instance, "maxDownloadRequests",     currentMaxRequests())
            patchFieldIfPresent(instance, "maxDownloadRequestsBig",  currentMaxRequests())
        })
    }

    private fun patchDownloadController() {
        val clazz = findClass(DOWNLOAD_CONTROLLER) ?: return

        hookAllMethods(clazz, "getInstance", afterHook { param ->
            if (!isEnabled(PrefManager.KEY_BOOST_DOWNLOAD)) return@afterHook
            val instance = param.result ?: return@afterHook
            patchFieldIfPresent(instance, "maxDownloadRequests",    currentMaxRequests())
            patchFieldIfPresent(instance, "maxDownloadRequestsBig", currentMaxRequests())
        })
    }

    private fun patchFieldIfPresent(obj: Any, fieldName: String, value: Int) {
        try {
            var c: Class<*>? = obj.javaClass
            while (c != null) {
                try {
                    val f = c.getDeclaredField(fieldName)
                    f.isAccessible = true
                    f.set(obj, value)
                    return
                } catch (_: NoSuchFieldException) {}
                c = c.superclass
            }
        } catch (e: Exception) {
            // silent — field names vary between builds
        }
    }
}
