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

public class ProlificSerialPortWrapper {
    public static boolean isDeviceTypeT(UsbSerialPort serialPort) {
        ProlificSerialDriver.ProlificSerialPort prolificSerialPort = (ProlificSerialDriver.ProlificSerialPort) serialPort;
        return prolificSerialPort.mDeviceType == ProlificSerialDriver.DeviceType.DEVICE_TYPE_T;
    }

    public static boolean isDeviceTypeHxn(UsbSerialPort serialPort) {
        ProlificSerialDriver.ProlificSerialPort prolificSerialPort = (ProlificSerialDriver.ProlificSerialPort) serialPort;
        return prolificSerialPort.mDeviceType == ProlificSerialDriver.DeviceType.DEVICE_TYPE_HXN;
    }
}
