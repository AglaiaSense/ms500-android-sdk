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

public final class MonotonicClock {

    private static final long NS_PER_MS = 1_000_000;

    private MonotonicClock() {
    }

    public static long millis() {
        return System.nanoTime() / NS_PER_MS;
    }

}
