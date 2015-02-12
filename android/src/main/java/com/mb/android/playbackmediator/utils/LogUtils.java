/*
 * Copyright (C) 2013 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mb.android.playbackmediator.utils;

import android.util.Log;

import com.mb.android.BuildConfig;
import com.mb.android.logging.AppLogger;
import com.mb.android.playbackmediator.cast.BaseCastManager;

/**
 * Provides a simple wrapper to control logging in development vs production environment. This
 * library should only use the wrapper methods that this class provides.
 */
public class LogUtils {

    private static final String LOG_PREFIX = "ccl_";
    private static final int LOG_PREFIX_LENGTH = LOG_PREFIX.length();
    private static final int MAX_LOG_TAG_LENGTH = 23;

    private LogUtils() {
    }

    public static String makeLogTag(String str) {
        if (str.length() > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1);
        }

        return LOG_PREFIX + str;
    }

    /**
     * WARNING: Don't use this when obfuscating class names with Proguard!
     */
    public static String makeLogTag(Class<?> cls) {
        return makeLogTag(cls.getSimpleName());
    }

    @SuppressWarnings("unused")
    public static void LOGD(final String tag, String message) {
        if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            AppLogger.getLogger().Debug(tag, getVersionPrefix() + message);
        }
    }

    @SuppressWarnings("unused")
    public static void LOGD(final String tag, String message, Throwable cause) {
        if (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            AppLogger.getLogger().Debug(tag, getVersionPrefix() + message, cause);
        }
    }

    public static void LOGE(final String tag, String message) {
        AppLogger.getLogger().Error(getVersionPrefix() + message);
    }

    public static void LOGE(final String tag, String message, Throwable cause) {
        AppLogger.getLogger().Error(getVersionPrefix() + message, cause);
    }

    public static String getVersionPrefix(){
        return "[v" + BaseCastManager.getCclVersion() + "] ";
    }

}
