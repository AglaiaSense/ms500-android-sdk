/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.WtaApi
import com.ms500.usbcam.api.WtaInfoApi
import com.ms500.usbcam.api.WtaPostprocessParamApi
import com.ms500.usbcam.api.WtaRegionParamApi

/**
 * Internal WTA facade.
 *
 * Pure grouping: it holds no logic of its own, so the facade adds no behaviour between the caller
 * and the WTA-specific APIs.
 */
internal class WtaApiImpl(
    override val info: WtaInfoApi,
    override val postprocessParam: WtaPostprocessParamApi,
    override val regionParam: WtaRegionParamApi
) : WtaApi
