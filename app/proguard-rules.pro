# Proguard rules para Jarvis App

# Mantener clases de la app
-keep class com.jarvis.app.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# llama.cpp JNI
-keep class com.jarvis.app.ai.llm.LlamaEngine { *; }
-keep class com.jarvis.app.ai.llm.TokenCallback { *; }
-keep class com.jarvis.app.ai.image.StableDiffusionEngine { *; }
-keep class com.jarvis.app.ai.vision.MoondreamEngine { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# iText PDF
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Markwon
-keep class io.noties.markwon.** { *; }

# Lottie
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Líneas de depuración en stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
