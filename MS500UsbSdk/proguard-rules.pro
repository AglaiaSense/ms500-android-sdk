# Obfuscation policy for the release AAR:
# - Keep the public SDK surface and all implementation classes stable.
# - Obfuscate only selected implementation internals: CdcWireFormat.kt and OtaPackageCore.kt.
-keep class !com.ms500.usbcam.cdc.CdcWireFormat,!com.ms500.usbcam.cdc.CdcWireFormat$*,!com.ms500.usbcam.ota.OtaPackageCore,!com.ms500.usbcam.ota.OtaPackageCore$*,com.ms500.usbcam.** { *; }

# CdcWireFormat.kt and OtaPackageCore.kt are intentionally allowed to be obfuscated.
-keep,allowobfuscation class com.ms500.usbcam.cdc.CdcWireFormat { *; }
-keep,allowobfuscation class com.ms500.usbcam.cdc.CdcWireFormat$* { *; }

-keep,allowobfuscation class com.ms500.usbcam.ota.OtaPackageCore { *; }
-keep,allowobfuscation class com.ms500.usbcam.ota.OtaPackageCore$* { *; }

# Preserve Kotlin/Java metadata that public generic APIs and annotations may rely on.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Keep source/line info to make SDK-side stack traces still debuggable after obfuscation.
-keepattributes SourceFile,LineNumberTable
