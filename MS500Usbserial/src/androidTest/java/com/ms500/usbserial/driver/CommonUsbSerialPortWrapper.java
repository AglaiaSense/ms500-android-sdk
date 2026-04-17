/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbserial.driver;

import android.hardware.usb.UsbRequest;

import com.ms500.usbserial.util.UsbUtils;

import java.util.LinkedList;

public class CommonUsbSerialPortWrapper {
    public static byte[] getWriteBuffer(UsbSerialPort serialPort) {
        CommonUsbSerialPort commonSerialPort = (CommonUsbSerialPort) serialPort;
        return commonSerialPort.mWriteBuffer;
    }

    public static LinkedList<UsbRequest> getReadQueueRequests(UsbSerialPort serialPort) {
        CommonUsbSerialPort commonSerialPort = (CommonUsbSerialPort) serialPort;
        return commonSerialPort.mReadQueueRequests;
    }

    public static void setReadQueueRequestSupplier(UsbSerialPort serialPort, UsbUtils.Supplier<UsbRequest> supplier) {
        CommonUsbSerialPort commonSerialPort = (CommonUsbSerialPort) serialPort;
        commonSerialPort.mUsbRequestSupplier = supplier;
    }
}
