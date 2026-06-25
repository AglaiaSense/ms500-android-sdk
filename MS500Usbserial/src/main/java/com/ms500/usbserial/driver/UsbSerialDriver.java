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

import android.hardware.usb.UsbDevice;

import java.util.List;

public interface UsbSerialDriver {

    /*
     * Additional interface properties. Invoked thru reflection.
     *
        UsbSerialDriver(UsbDevice device);                  // constructor with device
        static Map<Integer, int[]> getSupportedDevices();
        static boolean probe(UsbDevice device);             // optional
     */


    /**
     * Returns the raw {@link UsbDevice} backing this port.
     *
     * @return the device
     */
    UsbDevice getDevice();

    /**
     * Returns all available ports for this device. This list must have at least
     * one entry.
     *
     * @return the ports
     */
    List<UsbSerialPort> getPorts();
}
