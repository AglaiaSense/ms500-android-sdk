/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.callback

import android.hardware.usb.UsbDevice
import com.jiangdg.usb.USBMonitor

/**
 * I device connect call back
 *
 */
interface IDeviceConnectCallBack {
    /**
     * On attach dev
     *
     * @param device usb device
     */
    fun onAttachDev(device: UsbDevice?)

    /**
     * On detach dev
     *
     * @param device usb device
     */
    fun onDetachDec(device: UsbDevice?)

    /**
     * On connect dev
     *
     * @param device usb device
     */
    fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock? = null)

    /**
     * On dis connect dev
     *
     * @param device usb device
     */
    fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock? = null)

    /**
     * On cancel dev
     *
     * @param device usb device
     */
    fun onCancelDev(device: UsbDevice?)
}