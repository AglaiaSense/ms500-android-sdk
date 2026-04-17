/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.jiangdg.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HandlerThreadHandler extends Handler {
	private static final String TAG = "HandlerThreadHandler";

	public static final HandlerThreadHandler createHandler() {
		return createHandler(TAG);
	}

	public static final HandlerThreadHandler createHandler(final String name) {
		final HandlerThread thread = new HandlerThread(name);
		thread.start();
		return new HandlerThreadHandler(thread.getLooper());
	}

	public static final HandlerThreadHandler createHandler(@Nullable final Callback callback) {
		return createHandler(TAG, callback);
	}

	public static final HandlerThreadHandler createHandler(final String name, @Nullable final Callback callback) {
		final HandlerThread thread = new HandlerThread(name);
		thread.start();
		return new HandlerThreadHandler(thread.getLooper(), callback);
	}

	private HandlerThreadHandler(@NonNull final Looper looper) {
		super(looper);
	}

	private HandlerThreadHandler(@NonNull final Looper looper, @Nullable final Callback callback) {
		super(looper, callback);
	}

}
