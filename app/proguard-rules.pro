# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.nook.app.**$$serializer { *; }
-keepclassmembers class com.nook.app.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }

# Firestore maps documents onto these classes reflectively
-keep class com.nook.app.data.model.** { *; }

# LiveKit / WebRTC
-keep class livekit.org.webrtc.** { *; }
-keep class io.livekit.android.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.sip.**
-dontwarn gov.nist.**
