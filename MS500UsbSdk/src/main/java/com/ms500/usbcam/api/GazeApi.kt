/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api

/**
 * GAZE model facade, reached through `sdk.gaze`.
 *
 * Groups the APIs whose parameter ranges and result semantics are specific to the original GAZE
 * model. Shared capabilities - CDC, UVC, device info, camera, PQ, inference control, input tensor
 * and OTA - stay at the SDK root because they behave identically on both firmwares.
 *
 * **The facade selects the semantics, not the device.** Nothing here inspects what model is
 * attached: commands go out as built, parameters are validated against GAZE ranges, and results are
 * parsed with the GAZE parser. Callers learn the model from `deviceParam.getInfo()` - see
 * `DeviceParamDto.resolvedModelType` - and choose the matching facade. Calling this facade against
 * a WTA device is allowed and reports whatever the device actually answers; commands the WTA
 * firmware does not know come back as `SdkError.DeviceRejected` carrying the device's own message.
 *
 * Java callers should prefer the flat accessors on the SDK root
 * (`sdk.gazeInfoApi()`, `sdk.gazePostprocessParamApi()`) over chaining through this interface.
 */
interface GazeApi {

    /**
     * GAZE detection result streams.
     *
     * Boxes are in the **512x384 model tensor space**, which an overlay must map through the
     * 2028x1520 camera image to the centred 1920x1080 UVC frame.
     */
    val info: GazeInfoApi

    /**
     * GAZE post-process parameters.
     *
     * Exposes the full legacy field set. Fields that WTA firmware stores but never applies are
     * marked as such in their KDoc; they are still sent, because the device - not the SDK - decides
     * what it accepts.
     */
    val postprocessParam: PostprocessParamApi
}
