/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbcam.dto

import org.json.JSONObject

/**
 * Camera image size configuration
 */
data class PqConfigCameraImageSize(
    /** Width in pixels */
    val width: Int? = null,
    /** Height in pixels */
    val height: Int? = null,
    /** Scaling policy */
    val scalingPolicy: Int? = null
) {
    companion object {
        /**
         * Parse image size from JSON
         *
         * @param json JSON object
         * @return [PqConfigCameraImageSize]
         */
        fun fromJson(json: JSONObject): PqConfigCameraImageSize = PqConfigCameraImageSize(
            width = json.optInt("width").takeIf { json.has("width") },
            height = json.optInt("height").takeIf { json.has("height") },
            scalingPolicy = json.optInt("scaling_policy").takeIf { json.has("scaling_policy") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        scalingPolicy?.let { put("scaling_policy", it) }
    }
}

/**
 * Auto exposure configuration
 */
data class PqConfigAutoExposure(
    /** Maximum exposure time */
    val maxExposureTime: Int? = null,
    /** Minimum exposure time */
    val minExposureTime: Int? = null,
    /** Maximum gain */
    val maxGain: Int? = null,
    /** Convergence speed */
    val convergenceSpeed: Int? = null
) {
    companion object {
        /**
         * Parse auto exposure from JSON
         *
         * @param json JSON object
         * @return [PqConfigAutoExposure]
         */
        fun fromJson(json: JSONObject): PqConfigAutoExposure = PqConfigAutoExposure(
            maxExposureTime = json.optInt("max_exposure_time").takeIf { json.has("max_exposure_time") },
            minExposureTime = json.optInt("min_exposure_time").takeIf { json.has("min_exposure_time") },
            maxGain = json.optInt("max_gain").takeIf { json.has("max_gain") },
            convergenceSpeed = json.optInt("convergence_speed").takeIf { json.has("convergence_speed") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        maxExposureTime?.let { put("max_exposure_time", it) }
        minExposureTime?.let { put("min_exposure_time", it) }
        maxGain?.let { put("max_gain", it) }
        convergenceSpeed?.let { put("convergence_speed", it) }
    }
}

/**
 * Auto exposure metering configuration
 */
data class PqConfigAutoExposureMetering(
    /** Metering mode */
    val meteringMode: Int? = null,
    /** Top boundary */
    val top: Int? = null,
    /** Left boundary */
    val left: Int? = null,
    /** Bottom boundary */
    val bottom: Int? = null,
    /** Right boundary */
    val right: Int? = null
) {
    companion object {
        /**
         * Parse metering config from JSON
         *
         * @param json JSON object
         * @return [PqConfigAutoExposureMetering]
         */
        fun fromJson(json: JSONObject): PqConfigAutoExposureMetering = PqConfigAutoExposureMetering(
            meteringMode = json.optInt("metering_mode").takeIf { json.has("metering_mode") },
            top = json.optInt("top").takeIf { json.has("top") },
            left = json.optInt("left").takeIf { json.has("left") },
            bottom = json.optInt("bottom").takeIf { json.has("bottom") },
            right = json.optInt("right").takeIf { json.has("right") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        meteringMode?.let { put("metering_mode", it) }
        top?.let { put("top", it) }
        left?.let { put("left", it) }
        bottom?.let { put("bottom", it) }
        right?.let { put("right", it) }
    }
}

/**
 * Manual exposure configuration
 */
data class PqConfigManualExposure(
    /** Exposure time */
    val exposureTime: Int? = null,
    /** Gain value */
    val gain: Int? = null
) {
    companion object {
        /**
         * Parse manual exposure from JSON
         *
         * @param json JSON object
         * @return [PqConfigManualExposure]
         */
        fun fromJson(json: JSONObject): PqConfigManualExposure = PqConfigManualExposure(
            exposureTime = json.optInt("exposure_time").takeIf { json.has("exposure_time") },
            gain = json.optInt("gain").takeIf { json.has("gain") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        exposureTime?.let { put("exposure_time", it) }
        gain?.let { put("gain", it) }
    }
}

/**
 * Auto white balance configuration
 */
data class PqConfigAutoWhiteBalance(
    /** Convergence speed */
    val convergenceSpeed: Int? = null
) {
    companion object {
        /**
         * Parse auto white balance from JSON
         *
         * @param json JSON object
         * @return [PqConfigAutoWhiteBalance]
         */
        fun fromJson(json: JSONObject): PqConfigAutoWhiteBalance = PqConfigAutoWhiteBalance(
            convergenceSpeed = json.optInt("convergence_speed").takeIf { json.has("convergence_speed") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        convergenceSpeed?.let { put("convergence_speed", it) }
    }
}

/**
 * Manual white balance preset
 */
data class PqConfigManualWhiteBalancePreset(
    /** Color temperature (Kelvin) */
    val colorTemperature: Int? = null
) {
    companion object {
        /**
         * Parse manual WB preset from JSON
         *
         * @param json JSON object
         * @return [PqConfigManualWhiteBalancePreset]
         */
        fun fromJson(json: JSONObject): PqConfigManualWhiteBalancePreset = PqConfigManualWhiteBalancePreset(
            colorTemperature = json.optInt("color_temperature").takeIf { json.has("color_temperature") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        colorTemperature?.let { put("color_temperature", it) }
    }
}

/**
 * Image cropping configuration
 */
data class PqConfigImageCropping(
    /** Left offset */
    val left: Int? = null,
    /** Top offset */
    val top: Int? = null,
    /** Crop width */
    val width: Int? = null,
    /** Crop height */
    val height: Int? = null
) {
    companion object {
        /**
         * Parse image cropping from JSON
         *
         * @param json JSON object
         * @return [PqConfigImageCropping]
         */
        fun fromJson(json: JSONObject): PqConfigImageCropping = PqConfigImageCropping(
            left = json.optInt("left").takeIf { json.has("left") },
            top = json.optInt("top").takeIf { json.has("top") },
            width = json.optInt("width").takeIf { json.has("width") },
            height = json.optInt("height").takeIf { json.has("height") }
        )
    }

    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        left?.let { put("left", it) }
        top?.let { put("top", it) }
        width?.let { put("width", it) }
        height?.let { put("height", it) }
    }
}

/**
 * PQ settings collection
 */
data class PqConfigSettings(
    /** Camera image size */
    val cameraImageSize: PqConfigCameraImageSize? = null,
    /** Exposure mode */
    val exposureMode: Int? = null,
    /** Auto exposure parameters */
    val autoExposure: PqConfigAutoExposure? = null,
    /** Auto exposure metering parameters */
    val autoExposureMetering: PqConfigAutoExposureMetering? = null,
    /** EV compensation */
    val evCompensation: Int? = null,
    /** AE anti-flicker mode */
    val aeAntiFlickerMode: Int? = null,
    /** Manual exposure parameters */
    val manualExposure: PqConfigManualExposure? = null,
    /** White balance mode */
    val whiteBalanceMode: Int? = null,
    /** Auto white balance parameters */
    val autoWhiteBalance: PqConfigAutoWhiteBalance? = null,
    /** Manual white balance preset */
    val manualWhiteBalancePreset: PqConfigManualWhiteBalancePreset? = null,
    /** Image cropping parameters */
    val imageCropping: PqConfigImageCropping? = null,
    /** Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse PQ settings from JSON
         *
         * @param json JSON object
         * @return [PqConfigSettings]
         */
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

    /**
     * Convert to JSON
     */
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

/**
 * PQ configuration parameter
 */
data class PqcongifParam(
    /** Status */
    val status: String? = null,
    /** Command */
    val cmd: String? = null,
    /** PQ settings */
    val pqSettings: PqConfigSettings? = null,
    /** Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse PQ config parameter from JSON
         *
         * @param json JSON Object
         * @return [PqcongifParam]
         */
        fun fromJson(json: JSONObject): PqcongifParam = PqcongifParam(
            status = json.optString("status").ifBlank { null },
            cmd = json.optString("cmd").ifBlank { null },
            pqSettings = json.optJSONObject("pq_settings")?.let { PqConfigSettings.fromJson(it) },
            raw = json
        )
    }

    /**
     * Convert to setting JSON
     *
     * @param module Module name
     */
    fun toSetJson(module: String? = "pq_settings"): JSONObject = JSONObject().apply {
        module?.let { put("module", it) }
        pqSettings?.let { put("pq_settings", it.toJson()) }
    }
}
