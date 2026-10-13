package com.soligeram.module.core

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

/**
 * Loads and resolves obfuscated class/field/method names for a given
 * Telegram variant + version combo. Falls back to the real name if no
 * mapping entry exists (unobfuscated builds).
 *
 * *Private thought: I am the rosetta stone between source truth and the
 *  mangled binary — every lookup here is a small betrayal of minification.*
 */
object ObfuscationMapper {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class FieldEntry(val hint: String? = null, val obfuscated: String)

    @Serializable
    data class MethodEntry(val hint: String? = null, val obfuscated: String)

    @Serializable
    data class ClassEntry(
        val hint: String? = null,
        val obfuscated: String,
        val fields: Map<String, FieldEntry> = emptyMap(),
        val methods: Map<String, MethodEntry> = emptyMap()
    )

    @Serializable
    data class MappingsFile(
        val variant: String,
        val package_name: String,
        val version: String,
        val updated: String,
        val mappings: Map<String, ClassEntry>
    )

    private var mappings: Map<String, ClassEntry> = emptyMap()
    private var targetPackage: String = "org.telegram.messenger"

    fun load(assets: AssetManager, variant: String = "telegram") {
        try {
            val fileName = "obfuscation_mappings/$variant.json"
            val text = assets.open(fileName).use { InputStreamReader(it).readText() }
            val file = json.decodeFromString<MappingsFile>(text)
            mappings = file.mappings
            targetPackage = file.package_name
            SoliLog.i("ObfuscationMapper", "Loaded ${mappings.size} class mappings for $variant ${file.version}")
        } catch (e: Exception) {
            SoliLog.e("ObfuscationMapper", "Failed to load mappings: ${e.message}")
        }
    }

    /** Returns the obfuscated class name for the given canonical class name. */
    fun resolveClass(canonicalName: String): String =
        mappings[canonicalName]?.obfuscated ?: canonicalName

    /** Returns the obfuscated field name inside a class. */
    fun resolveField(canonicalClass: String, fieldName: String): String =
        mappings[canonicalClass]?.fields?.get(fieldName)?.obfuscated ?: fieldName

    /** Returns the obfuscated method name inside a class. */
    fun resolveMethod(canonicalClass: String, methodName: String): String =
        mappings[canonicalClass]?.methods?.get(methodName)?.obfuscated ?: methodName
}
