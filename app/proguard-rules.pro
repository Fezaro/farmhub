# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Production-safe rules for Retrofit/Gson models and cleaner release logs.

# Preserve line numbers for crash reporting.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations/signatures used by Retrofit, Gson, and generated code.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Keep API contracts that Retrofit reflects over.
-keep interface com.farm_tech.farmhub.api.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep app models used for JSON serialization/deserialization.
-keep class com.farm_tech.farmhub.models.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken — required for anonymous TypeToken subclasses used in repositories.
# Gson 2.9.x does NOT ship a consumer rule for TypeToken subclasses (added only in 2.10+).
# Without these rules, R8's class-merging optimisation can destroy the generic type signature
# that Gson reads via reflection, causing ClassCastException or JsonParseException at runtime.
#
# Rule 1: Preserve the TypeToken base class so anonymous subclasses can reference it.
-keep class com.google.gson.reflect.TypeToken { *; }
# Rule 2: Preserve every anonymous/named subclass of TypeToken so R8 cannot merge or inline them.
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Keep enum helpers used by generated/runtime code.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable creators when present.
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove low-priority logs from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Silence common optional warning noise.
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
