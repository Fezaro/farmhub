# Enhanced ProGuard/R8 Rules for FarmHub Release Build

## Purpose
These are production-optimized ProGuard rules for the FarmHub app to ensure proper code shrinking while maintaining app functionality.

## Add to: app/proguard-rules.pro

```proguard
# ============================================
# FarmHub Production ProGuard Rules
# ============================================

# ============================================
# 1. PRESERVE LINE NUMBERS FOR CRASH REPORTING
# ============================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# 2. PRESERVE ANNOTATIONS
# ============================================
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# ============================================
# 3. RETROFIT & NETWORK
# ============================================
-keep class com.farmtech.farmhub.api.** { *; }
-keep interface com.farmtech.farmhub.api.** { *; }
-keep class com.farmtech.farmhub.models.** { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback

# Keep Retrofit annotations
-keep @interface retrofit2.http.** { *; }

# ============================================
# 4. OKHTTP & HTTP LOGGING
# ============================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.** { *; }
-keep class okhttp3.logging.** { *; }

# Keep OkHttp interceptors
-keep class okhttp3.Interceptor
-keepclasseswithmembers class okhttp3.** {
    public <methods>;
}

# ============================================
# 5. GSON - JSON Serialization
# ============================================
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# Keep JSON model classes (data classes)
-keep class com.farmtech.farmhub.models.** {
    <fields>;
    <init>(...);
}

# Preserve GSON Field names
-keepclassmembers class * {
    @com.google.gson.annotations.* <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================
# 6. HILT - DEPENDENCY INJECTION
# ============================================
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.** class * { *; }
-keep class **_Hilt_* { *; }
-keep class * implements dagger.hilt.internal.IAliasHolder { *; }

# Keep Hilt-generated code
-keep class * extends dagger.hilt.android.AndroidEntryPoint
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# ============================================
# 7. ANDROIDX & JETPACK COMPOSE
# ============================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keepnames class androidx.** { *; }

# Compose runtime
-keep class androidx.compose.** { *; }
-keepclasseswithmembers class androidx.compose.** {
    public <methods>;
}

# Material Design Components
-keep class com.google.android.material.** { *; }
-keep interface com.google.android.material.** { *; }

# ============================================
# 8. KOTLIN-SPECIFIC
# ============================================
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep @interface kotlin.jvm.internal.** { *; }

# Keep data classes
-keepclassmembers class * {
    <init>(java.lang.String, ...);
}

# ============================================
# 9. ANDROID FRAMEWORK
# ============================================
-keep class android.** { *; }
-keepnames class android.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep View constructors for inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# ============================================
# 10. ENUMERATIONS
# ============================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# 11. EXOPLAYER - VIDEO PLAYBACK
# ============================================
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# ============================================
# 12. PLAY SERVICES - LOCATION
# ============================================
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-keep @interface com.google.android.gms.** { *; }

# ============================================
# 13. ENCRYPTED SHARED PREFERENCES
# ============================================
-keep class androidx.security.crypto.** { *; }

# ============================================
# 14. COIL - IMAGE LOADING
# ============================================
-keep class coil.** { *; }
-keep interface coil.** { *; }

# ============================================
# 15. REFLECTION-BASED LIBRARIES
# ============================================
# Keep generic signatures required for reflection
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================
# 16. OPTIMIZATION SETTINGS
# ============================================
# Enable aggressive optimization for better performance
-optimizationpasses 5
-allowaccessmodification

# Simplify bytecode by removing unreachable code
-mergeinterfacesaggressively

# ============================================
# 17. REMOVE DEBUG LOGGING IN RELEASE
# ============================================
# This rule removes Log.v, Log.d, Log.i calls in release builds
# Keep Log.w and Log.e for important issues
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ============================================
# 18. SUPPRESS WARNINGS (Use carefully)
# ============================================
-dontwarn androidx.compose.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.google.common.**
-dontwarn javax.annotation.**

# ============================================
# 19. AUTHENTICATION & SESSION MANAGEMENT
# ============================================
-keep class com.farmtech.farmhub.auth.** { *; }
-keep class com.farmtech.farmhub.session.** { *; }

# Preserve TokenValidator methods
-keepclassmembers class * {
    public boolean isTokenValid(...);
    public boolean isTokenExpired(...);
}

# ============================================
# 20. REPOSITORY PATTERN
# ============================================
-keep class com.farmtech.farmhub.repository.** { *; }
-keep interface com.farmtech.farmhub.repository.** { *; }

# ============================================
# 21. VIEWMODEL & LIFECYCLE
# ============================================
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ============================================
# 22. SERIALIZABLE CLASSES
# ============================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================
# 23. PARCELABLE CLASSES
# ============================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================
# END OF PROGUARD RULES
# ============================================
```

## Key Configuration Sections

### 1. Code Optimization
- **Aggressive optimization passes**: 5 (maximum safe value)
- **Access modification**: Allowed for optimization
- **Merge interfaces**: Enabled

### 2. Debug Code Removal
The following debug statements are REMOVED in release builds:
```kotlin
// These are REMOVED:
Log.v(...) // VERBOSE removed
Log.d(...) // DEBUG removed
Log.i(...) // INFO removed

// These are KEPT:
Log.w(...) // WARNING kept
Log.e(...) // ERROR kept
```

### 3. Libraries Preserved
- ✅ Retrofit (API communication)
- ✅ OkHttp (HTTP client)
- ✅ GSON (JSON parsing)
- ✅ Hilt (dependency injection)
- ✅ Jetpack Compose (UI framework)
- ✅ Androidx libraries
- ✅ ExoPlayer (video playback)
- ✅ Play Services (location)
- ✅ Encrypted SharedPreferences (secure storage)

### 4. Safe Optimizations
- ✅ Line numbers preserved (for crash reporting)
- ✅ Enum methods preserved
- ✅ Reflection attributes preserved
- ✅ Serializable/Parcelable support maintained

## Application Instructions

1. **Add to `app/proguard-rules.pro`**:
   - Copy all rules above (or just the sections you need)
   - Already commented and organized by category

2. **Verify in `app/build.gradle.kts`**:
   ```kotlin
   release {
       isMinifyEnabled = true          // Enabled ✅
       isShrinkResources = true        // Enabled ✅
       isDebuggable = false            // Disabled ✅
       proguardFiles(
           getDefaultProguardFile("proguard-android-optimize.txt"),
           "proguard-rules.pro"        // Your custom rules ✅
       )
   }
   ```

3. **Build and verify**:
   ```bash
   ./gradlew bundleRelease
   # Check that app still works correctly with all features
   ```

4. **Check mapping file**:
   - Location: `app/build/outputs/mapping/release/mapping.txt`
   - Use for crash report deobfuscation
   - Upload to Firebase Crashlytics for automatic deobfuscation

## Testing the Release Build

```bash
# Build release APK for testing on emulator/device
./gradlew installRelease

# Or build bundle and test with bundletool
./gradlew bundleRelease

bundletool build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app-test.apks \
  --mode=universal

bundletool install-apks --apks=app-test.apks
```

## Expected Build Size

- **Debug APK**: 20-30 MB
- **Release APK**: 10-20 MB (50% reduction due to shrinking)
- **Release AAB**: 5-15 MB (further optimized with split APKs)

Size reduction comes from:
- Dead code removal (~30% of original)
- Name obfuscation (smaller identifiers)
- Resource optimization
- R8 optimization passes

## Troubleshooting

**Issue**: App crashes after applying these rules
- Check which feature is missing`
- Add specific keep rule for the feature
- Run with `--verbose` to see ProGuard output

**Issue**: Network calls fail
- Verify Retrofit/OkHttp rules are present
- Check API models are preserved
- Ensure GSON rules are correct

**Issue**: Compose UI not rendering
- Verify Androidx Compose rules are present
- Check Material Design rules
- Ensure Navigation Compose is kept

**Issue**: Crashes during runtime
- Check crash logs from Play Console
- Deobfuscate using mapping.txt file
- Add `-verbose` flag to see obfuscation details

## Performance Impact

Expected improvements in release build:
- **App size**: 40-50% smaller than debug
- **Method count**: 30% reduction
- **Startup time**: 5-10% faster
- **Runtime**: No significant difference (already optimized by R8)

---

**Rules Version**: v1.0
**Last Updated**: June 1, 2026
**For SDK**: 36 (Android 16)

