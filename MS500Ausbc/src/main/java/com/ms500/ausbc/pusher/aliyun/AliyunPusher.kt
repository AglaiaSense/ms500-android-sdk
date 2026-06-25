/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.pusher.aliyun

import android.content.Context
import com.ms500.ausbc.pusher.IPusher
import com.ms500.ausbc.pusher.callback.IStateCallback
import com.ms500.ausbc.pusher.config.AusbcConfig

/** Your self pusher engine
 *
 */
class AliyunPusher: IPusher {
    override fun init(context: Context?, ausbcConfig: AusbcConfig?, callback: IStateCallback?) {
        TODO("Not yet implemented")
    }

    override fun start(url: String?) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }

    override fun reconnect() {
        TODO("Not yet implemented")
    }

    override fun reconnectUrl(url: String?) {
        TODO("Not yet implemented")
    }

    override fun pushStream(type: Int, data: ByteArray?, size: Int, pts: Long) {
        TODO("Not yet implemented")
    }

    override fun destroy() {
        TODO("Not yet implemented")
    }

    override fun isPushing(): Boolean {
        TODO("Not yet implemented")
    }
}