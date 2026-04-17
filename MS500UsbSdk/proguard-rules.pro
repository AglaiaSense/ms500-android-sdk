# Keep the public SDK entry and stable consumer-facing surface.
-keep class com.ms500.usbcam.MS500UsbCamSdk { *; }
-keep class com.ms500.usbcam.MS500UsbCamSdk$Companion { *; }
-keep class com.ms500.usbcam.api.CameraParamApi { *; }
-keep class com.ms500.usbcam.api.CdcConnectApi { *; }
-keep class com.ms500.usbcam.api.CdcStatusEvent { *; }
-keep class com.ms500.usbcam.api.CdcMessageEvent { *; }
-keep class com.ms500.usbcam.api.ConnectionState { *; }
-keep class com.ms500.usbcam.api.ConnectionState$* { *; }
-keep class com.ms500.usbcam.api.DeviceParamApi { *; }
-keep class com.ms500.usbcam.api.GazeInfoApi { *; }
-keep class com.ms500.usbcam.api.InferenceParamApi { *; }
-keep class com.ms500.usbcam.api.InputTensorApi { *; }
-keep class com.ms500.usbcam.api.OtaParamApi { *; }
-keep class com.ms500.usbcam.api.PostprocessParamApi { *; }
-keep class com.ms500.usbcam.api.PqConfigParamApi { *; }
-keep class com.ms500.usbcam.api.PreviewState { *; }
-keep class com.ms500.usbcam.api.PreviewState$* { *; }
-keep class com.ms500.usbcam.api.PreviewDevice { *; }
-keep class com.ms500.usbcam.api.PreviewSizeInfo { *; }
-keep class com.ms500.usbcam.api.CaptureCallback { *; }
-keep class com.ms500.usbcam.api.UvcPreviewApi { *; }
-keep class com.ms500.usbcam.dto.** { *; }
-keep class com.ms500.usbcam.result.** { *; }
-keep class com.ms500.usbcam.cdc.CdcPortConnection { *; }
-keep class com.ms500.usbcam.cdc.CdcPortConnection$* { *; }
-keep class com.ms500.usbcam.cdc.CdcReceiver { *; }
-keep class com.ms500.usbcam.cdc.CdcReceiver$* { *; }
-keep class com.ms500.usbcam.cdc.CdcSender { *; }
-keep class com.ms500.usbcam.cdc.CdcSender$* { *; }
-keep class com.ms500.usbcam.cdc.InputTensorCaptureResult { *; }
-keep class com.ms500.usbcam.cdc.InputTensorMetadata { *; }
-keep class com.ms500.usbcam.cdc.InputTensorSession { *; }
-keep class com.ms500.usbcam.cdc.InputTensorSession$* { *; }
-keep class com.ms500.usbcam.ota.OtaFileTransport { *; }
-keep class com.ms500.usbcam.ota.OtaFileTransport$* { *; }
-keep class com.ms500.usbcam.ota.OtaToolstools { *; }
-keep class com.ms500.usbcam.ota.OtaToolstools$* { *; }
-keep class com.ms500.usbcam.ota.OtaUnifiedManage { *; }
-keep class com.ms500.usbcam.ota.OtaUnifiedManage$* { *; }

# Preserve Kotlin/Java metadata that public generic APIs and annotations may rely on.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Keep source/line info to make SDK-side stack traces still debuggable after obfuscation.
-keepattributes SourceFile,LineNumberTable
