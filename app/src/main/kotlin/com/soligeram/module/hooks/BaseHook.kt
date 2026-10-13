package com.soligeram.module.hooks

import com.soligeram.module.core.ObfuscationMapper
import com.soligeram.module.core.ReflectUtils
import com.soligeram.module.core.SoliLog
import com.soligeram.module.service.PrefManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * All feature hooks extend this.  It provides:
 *   • findClass()     — resolves via ObfuscationMapper then ClassLoader
 *   • hookMethod()    — thin wrapper around XposedBridge.hookMethod
 *   • returnConstant()— XC_MethodReplacement helper
 *
 * *Private thought: every subclass is a tiny knife between a function
 *  and its caller — small, sharp, and positioned precisely.*
 */
abstract class BaseHook(protected val loader: ClassLoader) {

    protected val tag: String get() = javaClass.simpleName

    abstract fun install()

    // ── Class resolution ─────────────────────────────────────────────────

    protected fun findClass(canonicalName: String): Class<*>? {
        val resolved = ObfuscationMapper.resolveClass(canonicalName)
        return try {
            loader.loadClass(resolved)
        } catch (e: ClassNotFoundException) {
            // Try unobfuscated as fallback
            if (resolved != canonicalName) {
                try { loader.loadClass(canonicalName) }
                catch (_: ClassNotFoundException) { null }
            } else null
        }.also {
            if (it == null) SoliLog.w(tag, "Class not found: $canonicalName (resolved: $resolved)")
        }
    }

    // ── Method hooking ───────────────────────────────────────────────────

    protected fun hookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Any,
        callback: XC_MethodHook
    ) {
        val resolved = ObfuscationMapper.resolveMethod(clazz.name, methodName)
        try {
            XposedHelpers.findAndHookMethod(clazz, resolved, *paramTypes, callback)
        } catch (e: NoSuchMethodError) {
            SoliLog.w(tag, "Method not found: ${clazz.simpleName}.$methodName (obf: $resolved)")
        } catch (e: Exception) {
            SoliLog.e(tag, "hookMethod failed: ${e.message}")
        }
    }

    protected fun hookAllMethods(
        clazz: Class<*>,
        methodName: String,
        callback: XC_MethodHook
    ) {
        val resolved = ObfuscationMapper.resolveMethod(clazz.name, methodName)
        val found = XposedBridge.hookAllMethods(clazz, resolved, callback)
        if (found.isEmpty()) {
            SoliLog.w(tag, "hookAllMethods: no methods hooked for ${clazz.simpleName}.$methodName")
        }
    }

    // ── Quick callbacks ──────────────────────────────────────────────────

    protected fun beforeHook(block: (XC_MethodHook.MethodHookParam) -> Unit) =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) = block(param)
        }

    protected fun afterHook(block: (XC_MethodHook.MethodHookParam) -> Unit) =
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = block(param)
        }

    protected fun returnConstant(value: Any?) =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = value
            }
        }

    protected fun doNothing() =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null
            }
        }

    // ── Field helpers ────────────────────────────────────────────────────

    protected fun setField(obj: Any, canonicalClass: String, fieldName: String, value: Any?) {
        val resolved = ObfuscationMapper.resolveField(canonicalClass, fieldName)
        ReflectUtils.setFieldValue(obj, resolved, value)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> getField(obj: Any, canonicalClass: String, fieldName: String): T? {
        val resolved = ObfuscationMapper.resolveField(canonicalClass, fieldName)
        return ReflectUtils.getFieldValue<T>(obj, resolved)
    }

    // ── Pref shortcut ────────────────────────────────────────────────────

    protected fun isEnabled(key: String) = PrefManager.isEnabled(key)
}
