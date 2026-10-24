-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.simbridge.core.protocol.** { *; }
-keep class com.simbridge.core.model.** { *; }
