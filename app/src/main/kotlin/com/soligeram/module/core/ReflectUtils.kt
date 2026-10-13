package com.soligeram.module.core

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Zero-allocation-on-hot-path reflection cache.
 *
 * *Private thought: reflection is a lockpick — elegant when precise,
 *  catastrophic when you're picking the wrong door in the dark.*
 */
object ReflectUtils {

    private val fieldCache  = HashMap<String, Field>()
    private val methodCache = HashMap<String, Method>()

    /* ------------------------------------------------------------------ */
    /*  Class lookup                                                         */
    /* ------------------------------------------------------------------ */

    fun findClass(name: String, loader: ClassLoader): Class<*>? = try {
        loader.loadClass(name)
    } catch (e: ClassNotFoundException) {
        SoliLog.w("Reflect", "Class not found: $name")
        null
    }

    /* ------------------------------------------------------------------ */
    /*  Field access                                                         */
    /* ------------------------------------------------------------------ */

    fun getField(clazz: Class<*>, name: String): Field? {
        val key = "${clazz.name}#$name"
        return fieldCache.getOrPut(key) {
            try {
                var c: Class<*>? = clazz
                while (c != null) {
                    try {
                        val f = c.getDeclaredField(name)
                        f.isAccessible = true
                        return@getOrPut f
                    } catch (_: NoSuchFieldException) {}
                    c = c.superclass
                }
                SoliLog.w("Reflect", "Field not found: $key")
                return null
            } catch (e: Exception) {
                SoliLog.e("Reflect", "getField error for $key: ${e.message}")
                return null
            }
        }
    }

    fun <T> getFieldValue(obj: Any, name: String): T? = try {
        @Suppress("UNCHECKED_CAST")
        getField(obj.javaClass, name)?.get(obj) as T?
    } catch (e: Exception) {
        SoliLog.e("Reflect", "getFieldValue error $name: ${e.message}")
        null
    }

    fun setFieldValue(obj: Any, name: String, value: Any?) = try {
        getField(obj.javaClass, name)?.set(obj, value)
    } catch (e: Exception) {
        SoliLog.e("Reflect", "setFieldValue error $name: ${e.message}")
    }

    /* ------------------------------------------------------------------ */
    /*  Method access                                                        */
    /* ------------------------------------------------------------------ */

    fun getMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method? {
        val key = "${clazz.name}#$name(${paramTypes.joinToString { it.simpleName }})"
        return methodCache.getOrPut(key) {
            try {
                var c: Class<*>? = clazz
                while (c != null) {
                    try {
                        val m = c.getDeclaredMethod(name, *paramTypes)
                        m.isAccessible = true
                        return@getOrPut m
                    } catch (_: NoSuchMethodException) {}
                    c = c.superclass
                }
                SoliLog.w("Reflect", "Method not found: $key")
                return null
            } catch (e: Exception) {
                SoliLog.e("Reflect", "getMethod error for $key: ${e.message}")
                return null
            }
        }
    }

    fun invokeMethod(obj: Any, name: String, vararg args: Any?): Any? = try {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        getMethod(obj.javaClass, name, *types)?.invoke(obj, *args)
    } catch (e: Exception) {
        SoliLog.e("Reflect", "invokeMethod error $name: ${e.message}")
        null
    }
}
