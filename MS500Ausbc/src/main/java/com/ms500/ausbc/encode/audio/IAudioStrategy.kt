/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.encode.audio

import com.ms500.ausbc.encode.bean.RawData

/** Audio(pcm) collection context
 *
 */
interface IAudioStrategy {
    fun initAudioRecord()
    fun startRecording()
    fun stopRecording()
    fun releaseAudioRecord()
    fun read(): RawData?
    fun isRecording(): Boolean
    fun getSampleRate(): Int
    fun getAudioFormat(): Int
    fun getChannelCount(): Int
    fun getChannelConfig(): Int
}