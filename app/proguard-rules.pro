# Keep runtime metadata used by Retrofit and Gson.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# DTOs and repositories are serialized by field name at runtime.
-keep class com.example.suretouchapp.data.model.** { *; }
-keep class com.example.suretouchapp.data.repository.** { *; }
-keep class com.example.suretouchapp.data.api.** { *; }

# Retrofit service declarations are reflected at runtime.
-keep interface com.example.suretouchapp.data.api.ApiService { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson serialization
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * extends com.google.gson.TypeAdapter
-keepclassmembers enum * { *; }
