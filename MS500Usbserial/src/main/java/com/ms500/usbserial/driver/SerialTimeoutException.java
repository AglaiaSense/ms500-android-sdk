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

import java.io.InterruptedIOException;

/**
 * Signals that a timeout has occurred on serial write.
 * Similar to SocketTimeoutException.
 *
 * {@see InterruptedIOException#bytesTransferred} may contain bytes transferred
 */
public class SerialTimeoutException extends InterruptedIOException {
    public SerialTimeoutException(String s, int bytesTransferred) {
        super(s);
        this.bytesTransferred = bytesTransferred;
    }
}
