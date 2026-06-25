/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.pusher.config

import android.content.Context
import android.preference.PreferenceManager
import android.text.TextUtils
import com.ms500.ausbc.pusher.IPusher

/** Streaming Media Server Profile
 *
 */
class AusbcConfig {
    private var audioSampleRate = 0
    private var audioChannels = 0
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoRotation = 0
    private var videoStride = 0
    private var pusher: IPusher? = null
    private var videoFormat: VideoFormat? = null

    fun getServerURL(context: Context?): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(SERVER_URL, DEFAULT_SERVER_URL)
    }

    fun setServerURL(context: Context?, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SERVER_URL, if (TextUtils.isEmpty(value)) DEFAULT_SERVER_URL else value)
            .apply()
    }

    fun getAudioSampleRate(): Int {
        return audioSampleRate
    }

    fun setAudioSampleRate(audioSampleRate: Int) {
        this.audioSampleRate = audioSampleRate
    }

    fun getAudioChannels(): Int {
        return audioChannels
    }

    fun setAudioChannels(audioChannels: Int) {
        this.audioChannels = audioChannels
    }

    fun getVideoWidth(): Int {
        return videoWidth
    }

    fun setVideoWidth(videoWidth: Int) {
        this.videoWidth = videoWidth
    }

    fun getVideoHeight(): Int {
        return videoHeight
    }

    fun setVideoHeight(videoHeight: Int) {
        this.videoHeight = videoHeight
    }

    fun getVideoRotation(): Int {
        return videoRotation
    }

    fun setVideoRotation(videoRotation: Int) {
        this.videoRotation = videoRotation
    }

    fun getVideoStride(): Int {
        return videoStride
    }

    fun setVideoStride(videoStride: Int) {
        this.videoStride = videoStride
    }

    /**Set third-party streaming engine
     *
     *@ param pusher inherits from IPusher third-party engine
     */
    fun setPusher(pusher: IPusher?) {
        this.pusher = pusher
    }

    fun getPusher(): IPusher? {
        return pusher
    }

    fun getVideoFormat() = videoFormat

    fun setVideoFormat(format: VideoFormat) {
        this.videoFormat = format
    }

    override fun toString(): String {
        return "AusbcConfig{" +
                "audioSampleRate=" + audioSampleRate +
                ", audioChannels=" + audioChannels +
                ", videoWidth=" + videoWidth +
                ", videoHeight=" + videoHeight +
                ", videoRotation=" + videoRotation +
                ", videoStride=" + videoStride +
                ", pusher=" + pusher +
                '}'
    }

    companion object {
        private const val SERVER_URL = "serverUrl"

        @JvmField
        var DEFAULT_SERVER_URL: String? = null
    }
}

enum class VideoFormat {
    NV21, RGBA
}