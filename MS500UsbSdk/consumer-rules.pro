# Keep the SDK API and implementation stable in consumer app builds.
# CdcWireFormat.kt and OtaPackageCore.kt are intentionally excluded so they may remain obfuscated when minification is applied.
-keep class !com.ms500.usbcam.cdc.CdcWireFormat,!com.ms500.usbcam.cdc.CdcWireFormat$*,!com.ms500.usbcam.ota.OtaPackageCore,!com.ms500.usbcam.ota.OtaPackageCore$*,com.ms500.usbcam.** { *; }

# CdcWireFormat.kt generates CdcWireFormat and CdcWireFormat$* classes; OtaPackageCore.kt contains OTA package parsing internals. Allow only these internals to be obfuscated.
-keep,allowobfuscation class com.ms500.usbcam.cdc.CdcWireFormat { *; }
-keep,allowobfuscation class com.ms500.usbcam.cdc.CdcWireFormat$* { *; }

-keep,allowobfuscation class com.ms500.usbcam.ota.OtaPackageCore { *; }
-keep,allowobfuscation class com.ms500.usbcam.ota.OtaPackageCore$* { *; }

# Preserve Kotlin and Java metadata needed by generic APIs and reflection.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
