package com.ms500.usbcam.dto

import org.json.JSONObject

data class PqConfigCameraImageSize(
    val width: Int? = null,
    val height: Int? = null,
    val scalingPolicy: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigCameraImageSize = PqConfigCameraImageSize(
            width = json.optInt("width").takeIf { json.has("width") },
            height = json.optInt("height").takeIf { json.has("height") },
            scalingPolicy = json.optInt("scaling_policy").takeIf { json.has("scaling_policy") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        scalingPolicy?.let { put("scaling_policy", it) }
    }
}

data class PqConfigAutoExposure(
    val maxExposureTime: Int? = null,
    val minExposureTime: Int? = null,
    val maxGain: Int? = null,
    val convergenceSpeed: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigAutoExposure = PqConfigAutoExposure(
            maxExposureTime = json.optInt("max_exposure_time").takeIf { json.has("max_exposure_time") },
            minExposureTime = json.optInt("min_exposure_time").takeIf { json.has("min_exposure_time") },
            maxGain = json.optInt("max_gain").takeIf { json.has("max_gain") },
            convergenceSpeed = json.optInt("convergence_speed").takeIf { json.has("convergence_speed") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        maxExposureTime?.let { put("max_exposure_time", it) }
        minExposureTime?.let { put("min_exposure_time", it) }
        maxGain?.let { put("max_gain", it) }
        convergenceSpeed?.let { put("convergence_speed", it) }
    }
}

data class PqConfigAutoExposureMetering(
    val meteringMode: Int? = null,
    val top: Int? = null,
    val left: Int? = null,
    val bottom: Int? = null,
    val right: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigAutoExposureMetering = PqConfigAutoExposureMetering(
            meteringMode = json.optInt("metering_mode").takeIf { json.has("metering_mode") },
            top = json.optInt("top").takeIf { json.has("top") },
            left = json.optInt("left").takeIf { json.has("left") },
            bottom = json.optInt("bottom").takeIf { json.has("bottom") },
            right = json.optInt("right").takeIf { json.has("right") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        meteringMode?.let { put("metering_mode", it) }
        top?.let { put("top", it) }
        left?.let { put("left", it) }
        bottom?.let { put("bottom", it) }
        right?.let { put("right", it) }
    }
}

data class PqConfigManualExposure(
    val exposureTime: Int? = null,
    val gain: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigManualExposure = PqConfigManualExposure(
            exposureTime = json.optInt("exposure_time").takeIf { json.has("exposure_time") },
            gain = json.optInt("gain").takeIf { json.has("gain") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        exposureTime?.let { put("exposure_time", it) }
        gain?.let { put("gain", it) }
    }
}

data class PqConfigAutoWhiteBalance(
    val convergenceSpeed: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigAutoWhiteBalance = PqConfigAutoWhiteBalance(
            convergenceSpeed = json.optInt("convergence_speed").takeIf { json.has("convergence_speed") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        convergenceSpeed?.let { put("convergence_speed", it) }
    }
}

data class PqConfigManualWhiteBalancePreset(
    val colorTemperature: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigManualWhiteBalancePreset = PqConfigManualWhiteBalancePreset(
            colorTemperature = json.optInt("color_temperature").takeIf { json.has("color_temperature") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        colorTemperature?.let { put("color_temperature", it) }
    }
}

data class PqConfigImageCropping(
    val left: Int? = null,
    val top: Int? = null,
    val width: Int? = null,
    val height: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigImageCropping = PqConfigImageCropping(
            left = json.optInt("left").takeIf { json.has("left") },
            top = json.optInt("top").takeIf { json.has("top") },
            width = json.optInt("width").takeIf { json.has("width") },
            height = json.optInt("height").takeIf { json.has("height") }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        left?.let { put("left", it) }
        top?.let { put("top", it) }
        width?.let { put("width", it) }
        height?.let { put("height", it) }
    }
}

data class PqConfigSettings(
    val cameraImageSize: PqConfigCameraImageSize? = null,
    val exposureMode: Int? = null,
    val autoExposure: PqConfigAutoExposure? = null,
    val autoExposureMetering: PqConfigAutoExposureMetering? = null,
    val evCompensation: Int? = null,
    val aeAntiFlickerMode: Int? = null,
    val manualExposure: PqConfigManualExposure? = null,
    val whiteBalanceMode: Int? = null,
    val autoWhiteBalance: PqConfigAutoWhiteBalance? = null,
    val manualWhiteBalancePreset: PqConfigManualWhiteBalancePreset? = null,
    val imageCropping: PqConfigImageCropping? = null,
    val raw: JSONObject? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqConfigSettings = PqConfigSettings(
            cameraImageSize = json.optJSONObject("camera_image_size")?.let { PqConfigCameraImageSize.fromJson(it) },
            exposureMode = json.optInt("exposure_mode").takeIf { json.has("exposure_mode") },
            autoExposure = json.optJSONObject("auto_exposure")?.let { PqConfigAutoExposure.fromJson(it) },
            autoExposureMetering = json.optJSONObject("auto_exposure_metering")?.let { PqConfigAutoExposureMetering.fromJson(it) },
            evCompensation = json.optInt("ev_compensation").takeIf { json.has("ev_compensation") },
            aeAntiFlickerMode = json.optInt("ae_anti_flicker_mode").takeIf { json.has("ae_anti_flicker_mode") },
            manualExposure = json.optJSONObject("manual_exposure")?.let { PqConfigManualExposure.fromJson(it) },
            whiteBalanceMode = json.optInt("white_balance_mode").takeIf { json.has("white_balance_mode") },
            autoWhiteBalance = json.optJSONObject("auto_white_balance")?.let { PqConfigAutoWhiteBalance.fromJson(it) },
            manualWhiteBalancePreset = json.optJSONObject("manual_white_balance_preset")?.let { PqConfigManualWhiteBalancePreset.fromJson(it) },
            imageCropping = json.optJSONObject("image_cropping")?.let { PqConfigImageCropping.fromJson(it) },
            raw = json
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        cameraImageSize?.let { put("camera_image_size", it.toJson()) }
        exposureMode?.let { put("exposure_mode", it) }
        autoExposure?.let { put("auto_exposure", it.toJson()) }
        autoExposureMetering?.let { put("auto_exposure_metering", it.toJson()) }
        evCompensation?.let { put("ev_compensation", it) }
        aeAntiFlickerMode?.let { put("ae_anti_flicker_mode", it) }
        manualExposure?.let { put("manual_exposure", it.toJson()) }
        whiteBalanceMode?.let { put("white_balance_mode", it) }
        autoWhiteBalance?.let { put("auto_white_balance", it.toJson()) }
        manualWhiteBalancePreset?.let { put("manual_white_balance_preset", it.toJson()) }
        imageCropping?.let { put("image_cropping", it.toJson()) }
    }
}

data class PqcongifParam(
    val status: String? = null,
    val cmd: String? = null,
    val pqSettings: PqConfigSettings? = null,
    val raw: JSONObject? = null
) {
    companion object {
        fun fromJson(json: JSONObject): PqcongifParam = PqcongifParam(
            status = json.optString("status").ifBlank { null },
            cmd = json.optString("cmd").ifBlank { null },
            pqSettings = json.optJSONObject("pq_settings")?.let { PqConfigSettings.fromJson(it) },
            raw = json
        )
    }

    fun toSetJson(module: String? = "pq_settings"): JSONObject = JSONObject().apply {
        module?.let { put("module", it) }
        pqSettings?.let { put("pq_settings", it.toJson()) }
    }
}
