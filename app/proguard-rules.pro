# FocusShield ProGuard / R8 Rules

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.feature.update.domain.** { *; }
-keepclassmembers class com.example.feature.update.domain.** { *; }
-keep class com.example.feature.rewards.domain.** { *; }
-keepclassmembers class com.example.feature.rewards.domain.** { *; }

# Room entities
-keep class com.example.data.local.entity.** { *; }
-keepclassmembers class com.example.data.local.entity.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Kotlin serialization
-keepclassmembers class kotlin.Metadata { *; }

# Compose
-keep class androidx.compose.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**


# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keep class * extends androidx.datastore.preferences.core.Preferences$Key { *; }

# Enum entries
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# R8 full mode
-allowaccessmodification
-repackageclasses ''
