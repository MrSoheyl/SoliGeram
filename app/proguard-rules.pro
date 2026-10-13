# Keep LSPosed / Xposed entry point
-keep class com.telcom.module.MainHook { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }
-keep class de.robv.android.xposed.** { *; }
-keep class io.github.libxposed.** { *; }

# Keep Room entities & DAOs
-keep class com.telcom.module.data.** { *; }

# Keep serialization models (obfuscation mappings)
-keep class com.telcom.module.core.ObfuscationMapper.** { *; }
-keepattributes *Annotation*

# Kotlin serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# Keep all hook classes (they use reflection internally)
-keep class com.telcom.module.hooks.** { *; }
-keep class com.telcom.module.service.** { *; }
