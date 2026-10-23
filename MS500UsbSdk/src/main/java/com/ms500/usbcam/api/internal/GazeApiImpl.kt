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

import com.ms500.usbcam.api.GazeApi
import com.ms500.usbcam.api.GazeInfoApi
import com.ms500.usbcam.api.PostprocessParamApi

/**
 * Internal GAZE facade.
 *
 * Pure grouping: it holds no logic of its own, so the facade adds no behaviour between the caller
 * and the GAZE-specific APIs.
 */
internal class GazeApiImpl(
    override val info: GazeInfoApi,
    override val postprocessParam: PostprocessParamApi
) : GazeApi
