# Keep the public SDK entry and API surface stable in consumer builds.
-keep class com.ms500.usbcam.MS500UsbCamSdk { *; }
-keep class com.ms500.usbcam.api.** { *; }
-keep class com.ms500.usbcam.dto.** { *; }

# Preserve Kotlin and Java metadata needed by generic APIs and reflection.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
