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
 * 图像尺寸配置 / Camera image size configuration
 */
data class PqConfigCameraImageSize(
    /** 宽度（像素） / Width in pixels */
    val width: Int? = null,
    /** 高度（像素） / Height in pixels */
    val height: Int? = null,
    /** 缩放策略 / Scaling policy */
    val scalingPolicy: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析图像尺寸 / Parse image size from JSON
         *
         * @param json JSON 对象
         * @return [PqConfigCameraImageSize]
         */
        fun fromJson(json: JSONObject): PqConfigCameraImageSize = PqConfigCameraImageSize(
            width = json.optInt("width").takeIf { json.has("width") },
            height = json.optInt("height").takeIf { json.has("height") },
            scalingPolicy = json.optInt("scaling_policy").takeIf { json.has("scaling_policy") }
        )
    }

    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        scalingPolicy?.let { put("scaling_policy", it) }
    }
}

/**
 * 自动曝光配置 / Auto exposure configuration
 */
data class PqConfigAutoExposure(
    /** 最大曝光时间 / Maximum exposure time */
    val maxExposureTime: Int? = null,
    /** 最小曝光时间 / Minimum exposure time */
    val minExposureTime: Int? = null,
    /** 最大增益 / Maximum gain */
    val maxGain: Int? = null,
    /** 收敛速度 / Convergence speed */
    val convergenceSpeed: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析自动曝光配置 / Parse auto exposure from JSON
         *
         * @param json JSON 对象
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
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        maxExposureTime?.let { put("max_exposure_time", it) }
        minExposureTime?.let { put("min_exposure_time", it) }
        maxGain?.let { put("max_gain", it) }
        convergenceSpeed?.let { put("convergence_speed", it) }
    }
}

/**
 * 自动曝光测光配置 / Auto exposure metering configuration
 */
data class PqConfigAutoExposureMetering(
    /** 测光模式 / Metering mode */
    val meteringMode: Int? = null,
    /** 测光区域上边界 / Top boundary */
    val top: Int? = null,
    /** 测光区域左边界 / Left boundary */
    val left: Int? = null,
    /** 测光区域下边界 / Bottom boundary */
    val bottom: Int? = null,
    /** 测光区域右边界 / Right boundary */
    val right: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析测光配置 / Parse metering config from JSON
         *
         * @param json JSON 对象
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
     * 转换为 JSON / Convert to JSON
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
 * 手动曝光配置 / Manual exposure configuration
 */
data class PqConfigManualExposure(
    /** 曝光时间 / Exposure time */
    val exposureTime: Int? = null,
    /** 增益值 / Gain value */
    val gain: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析手动曝光配置 / Parse manual exposure from JSON
         *
         * @param json JSON 对象
         * @return [PqConfigManualExposure]
         */
        fun fromJson(json: JSONObject): PqConfigManualExposure = PqConfigManualExposure(
            exposureTime = json.optInt("exposure_time").takeIf { json.has("exposure_time") },
            gain = json.optInt("gain").takeIf { json.has("gain") }
        )
    }

    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        exposureTime?.let { put("exposure_time", it) }
        gain?.let { put("gain", it) }
    }
}

/**
 * 自动白平衡配置 / Auto white balance configuration
 */
data class PqConfigAutoWhiteBalance(
    /** 收敛速度 / Convergence speed */
    val convergenceSpeed: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析自动白平衡配置 / Parse auto white balance from JSON
         *
         * @param json JSON 对象
         * @return [PqConfigAutoWhiteBalance]
         */
        fun fromJson(json: JSONObject): PqConfigAutoWhiteBalance = PqConfigAutoWhiteBalance(
            convergenceSpeed = json.optInt("convergence_speed").takeIf { json.has("convergence_speed") }
        )
    }

    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        convergenceSpeed?.let { put("convergence_speed", it) }
    }
}

/**
 * 手动白平衡预设 / Manual white balance preset
 */
data class PqConfigManualWhiteBalancePreset(
    /** 色温（K） / Color temperature (Kelvin) */
    val colorTemperature: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析手动白平衡预设 / Parse manual WB preset from JSON
         *
         * @param json JSON 对象
         * @return [PqConfigManualWhiteBalancePreset]
         */
        fun fromJson(json: JSONObject): PqConfigManualWhiteBalancePreset = PqConfigManualWhiteBalancePreset(
            colorTemperature = json.optInt("color_temperature").takeIf { json.has("color_temperature") }
        )
    }

    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        colorTemperature?.let { put("color_temperature", it) }
    }
}

/**
 * 图像裁剪配置 / Image cropping configuration
 */
data class PqConfigImageCropping(
    /** 左偏移 / Left offset */
    val left: Int? = null,
    /** 上偏移 / Top offset */
    val top: Int? = null,
    /** 裁剪宽度 / Crop width */
    val width: Int? = null,
    /** 裁剪高度 / Crop height */
    val height: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析图像裁剪配置 / Parse image cropping from JSON
         *
         * @param json JSON 对象
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
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        left?.let { put("left", it) }
        top?.let { put("top", it) }
        width?.let { put("width", it) }
        height?.let { put("height", it) }
    }
}

/**
 * PQ 设置集合 / PQ settings collection
 */
data class PqConfigSettings(
    /** 图像尺寸 / Camera image size */
    val cameraImageSize: PqConfigCameraImageSize? = null,
    /** 曝光模式 / Exposure mode */
    val exposureMode: Int? = null,
    /** 自动曝光参数 / Auto exposure parameters */
    val autoExposure: PqConfigAutoExposure? = null,
    /** 自动曝光测光参数 / Auto exposure metering parameters */
    val autoExposureMetering: PqConfigAutoExposureMetering? = null,
    /** 曝光补偿 / EV compensation */
    val evCompensation: Int? = null,
    /** 抗闪烁模式 / AE anti-flicker mode */
    val aeAntiFlickerMode: Int? = null,
    /** 手动曝光参数 / Manual exposure parameters */
    val manualExposure: PqConfigManualExposure? = null,
    /** 白平衡模式 / White balance mode */
    val whiteBalanceMode: Int? = null,
    /** 自动白平衡参数 / Auto white balance parameters */
    val autoWhiteBalance: PqConfigAutoWhiteBalance? = null,
    /** 手动白平衡预设 / Manual white balance preset */
    val manualWhiteBalancePreset: PqConfigManualWhiteBalancePreset? = null,
    /** 图像裁剪参数 / Image cropping parameters */
    val imageCropping: PqConfigImageCropping? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析 PQ 设置 / Parse PQ settings from JSON
         *
         * @param json JSON 对象
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
     * 转换为 JSON / Convert to JSON
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
 * PQ 配置参数 / PQ configuration parameter
 */
data class PqcongifParam(
    /** 状态 / Status */
    val status: String? = null,
    /** 命令 / Command */
    val cmd: String? = null,
    /** PQ 设置 / PQ settings */
    val pqSettings: PqConfigSettings? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析 PQ 配置参数 / Parse PQ config parameter from JSON
         *
         * @param json JSON 对象
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
     * 转换为设置用 JSON / Convert to setting JSON
     *
     * @param module 模块名称
     */
    fun toSetJson(module: String? = "pq_settings"): JSONObject = JSONObject().apply {
        module?.let { put("module", it) }
        pqSettings?.let { put("pq_settings", it.toJson()) }
    }
}
