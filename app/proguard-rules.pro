# Keep runtime metadata used by Retrofit and Gson.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# DTOs are serialized by field name at runtime. Keeping this package makes the
# production shrinker safe even for fields without an explicit @SerializedName.
-keep class com.example.suretouchapp.data.model.** { *; }

# Retrofit service declarations are reflected at runtime.
-keep interface com.example.suretouchapp.data.api.ApiService { *; }
