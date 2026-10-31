# Mira keeps its Room entities and kotlinx.serialization models intact under R8.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.rayluxeo.aida.**$$serializer { *; }
-keepclassmembers class com.rayluxeo.aida.** { *** Companion; }
-keepclasseswithmembers class com.rayluxeo.aida.** { kotlinx.serialization.KSerializer serializer(...); }
