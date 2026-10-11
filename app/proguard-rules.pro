# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.suitanglu.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.suitanglu.app.**$$serializer { *; }
