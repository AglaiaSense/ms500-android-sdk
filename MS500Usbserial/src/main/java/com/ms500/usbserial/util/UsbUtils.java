/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbserial.util;

import android.hardware.usb.UsbDeviceConnection;

import java.util.ArrayList;

public class UsbUtils {

    // copied from java.util.function.Supplier which is not available < API 24
    public interface Supplier<T> {
        T get();
    }

    private UsbUtils() {
    }

    public static  ArrayList<byte[]> getDescriptors(UsbDeviceConnection connection) {
        ArrayList<byte[]> descriptors = new ArrayList<>();
        byte[] rawDescriptors = connection.getRawDescriptors();
        if (rawDescriptors != null) {
            int pos = 0;
            while (pos < rawDescriptors.length) {
                int len = rawDescriptors[pos] & 0xFF;
                if (len == 0)
                    break;
                if (pos + len > rawDescriptors.length)
                    len = rawDescriptors.length - pos;
                byte[] descriptor = new byte[len];
                System.arraycopy(rawDescriptors, pos, descriptor, 0, len);
                descriptors.add(descriptor);
                pos += len;
            }
        }
        return descriptors;
    }

}
