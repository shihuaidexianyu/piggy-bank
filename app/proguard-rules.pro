# Money app ProGuard/R8 rules.
# General debugging aid - keep line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations (Room, kotlinx-serialization, Compose).
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,Signature,InnerClasses,EnclosingMethod

# === Room ===
# Room generated classes (Dao, Entity, Database) are accessed by reflection.
-keep class com.shihuaidexianyu.money.data.db.MoneyDatabase { *; }
-keep class com.shihuaidexianyu.money.data.db.* { *; }
-keep class com.shihuaidexianyu.money.data.dao.* { *; }
-keep class com.shihuaidexianyu.money.data.entity.* { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# === kotlinx-serialization ===
# Keep @Serializable classes and generated serializers.
-keepattributes *Annotation*
-keepclassmembers class com.shihuaidexianyu.money.domain.model.**$$serializer { *; }
-keepclasseswithmembers class com.shihuaidexianyu.money.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shihuaidexianyu.money.domain.model.**$$serializer { *; }
-keepclassmembers class com.shihuaidexianyu.money.domain.model.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.shihuaidexianyu.money.domain.model.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# === Kotlin metadata ===
-keep class kotlin.Metadata { *; }
-keepattributes KotlinMetadata

# === Compose ===
# Don't warn about Compose preview annotations that are stripped at runtime.
-dontwarn androidx.compose.runtime.**

# === App entry points ===
-keep class com.shihuaidexianyu.money.MoneyApplication { <init>(); }
-keep class com.shihuaidexianyu.money.MainActivity { <init>(); }

# === FileProvider (referenced from manifest) ===
-keep class androidx.core.content.FileProvider { *; }

# === Coroutines ===
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
