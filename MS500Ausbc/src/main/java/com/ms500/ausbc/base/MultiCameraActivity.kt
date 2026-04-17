/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.base

import android.content.Context
import android.hardware.usb.UsbDevice
import com.ms500.ausbc.MultiCameraClient
import com.ms500.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.usb.USBMonitor

/** Multi-road camera activity
 *
 */
abstract class MultiCameraActivity: BaseActivity() {
    private var mCameraClient: MultiCameraClient? = null
    private val mCameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()

    override fun initData() {
        mCameraClient = MultiCameraClient(this, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                generateCamera(this@MultiCameraActivity, device).apply {
                    mCameraMap[device.deviceId] = this
                    onCameraAttached(this)
                }
                if (isAutoRequestPermission()) {
                    mCameraClient?.requestPermission(device)
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                    onCameraDetached(this)
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                mCameraMap[device.deviceId]?.apply {
                    setUsbControlBlock(ctrlBlock)
                    onCameraConnected(this)
                }
            }

            override fun onDisConnectDec(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?
            ) {
                mCameraMap[device?.deviceId]?.apply {
                    onCameraDisConnected(this)
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                mCameraMap[device?.deviceId]?.apply {
                    onCameraDisConnected(this)
                }
            }
        })
        mCameraClient?.register()
    }

    override fun clear() {
        mCameraMap.values.forEach {
            it.closeCamera()
        }
        mCameraMap.clear()
        mCameraClient?.unRegister()
        mCameraClient?.destroy()
        mCameraClient = null
    }

    /**
     * Generate camera
     *
     * @param ctx context [Context]
     * @param device Usb device, see [UsbDevice]
     * @return Inheritor assignment camera api policy
     */
    abstract fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera

    /**
     * On camera connected
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraConnected(camera: MultiCameraClient.ICamera)

    /**
     * On camera disconnected
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraDisConnected(camera: MultiCameraClient.ICamera)

    /**
     * On camera attached
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraAttached(camera: MultiCameraClient.ICamera)

    /**
     * On camera detached
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraDetached(camera: MultiCameraClient.ICamera)

    /**
     * Get current connected cameras
     */
    protected fun getCameraMap() = mCameraMap

    /**
     * Get all usb device list
     */
    protected fun getDeviceList() = mCameraClient?.getDeviceList()

    /**
     * Get camera client
     */
    protected fun getCameraClient() = mCameraClient

    /**
     * If you want to open the specified camera,you need to let isAutoRequestPermission() false.
     *  And then you need to call requestPermission(device) in your own Fragment
     * when onAttachDev() called, default is true.
     */
    protected fun isAutoRequestPermission() = true

    /**
     * Request permission
     *
     * @param device see [UsbDevice]
     */
    protected fun requestPermission(device: UsbDevice?) {
        mCameraClient?.requestPermission(device)
    }

    /**
     * Has permission
     *
     * @param device see [UsbDevice]
     */
    protected fun hasPermission(device: UsbDevice?) = mCameraClient?.hasPermission(device) == true

    protected fun openDebug(debug: Boolean) {
        mCameraClient?.openDebug(debug)
    }
}