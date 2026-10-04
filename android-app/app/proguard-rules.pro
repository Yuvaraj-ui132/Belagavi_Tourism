# Belagavi Smart Tourism - Production ProGuard Rules

# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# Keep Jetpack Compose internals
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Firebase Auth and Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep data models used with Firestore / serialization
-keep class com.belagavi.tourism.data.model.** { *; }
-keep class com.belagavi.tourism.data.repository.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Coil image loading
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**

# Remove verbose logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep app entry point
-keep class com.belagavi.tourism.MainActivity { *; }
-keep class com.belagavi.tourism.SmartTourismApp { *; }

# Retrofit & Gson
# -keepattributes Signature alone is insufficient in R8.
# EnclosingMethod + InnerClasses are required so that Gson can call
# getGenericSuperclass() on anonymous TypeToken subclasses and get a
# ParameterizedType instead of a raw Class (fixes the release-only crash:
# "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType")
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep OkHttp response converter internals used by Retrofit's GsonConverterFactory
-keep class okhttp3.internal.** { *; }
-dontwarn okhttp3.internal.**

# =============================================================================
# FIX: R8 full mode + Retrofit 2.9.0 + suspend functions
# =============================================================================
# R8 full mode's bundled Retrofit rule uses -keep,allowobfuscation which permits
# the JVM Signature bytecode attribute of the AiApiService method to be stripped.
# When Retrofit processes a suspend fun returning Response<T>, it inspects the
# Continuation<Response<T>> parameter's generic type via reflection. If the
# Continuation generic argument is stripped by R8, getGenericParameterTypes()
# yields a raw Class instead of ParameterizedType, causing:
#   java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
#
# Fix: explicitly keep the Retrofit interface, Continuation, Call, and Response
# so that the Signature attribute and all generic type parameters are preserved.
-keep interface com.belagavi.tourism.data.network.AiApiService { *; }
-keep interface kotlin.coroutines.Continuation { *; }
-keep interface retrofit2.Call { *; }
-keep class retrofit2.Response { *; }
