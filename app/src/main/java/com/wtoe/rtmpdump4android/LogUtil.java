package com.wtoe.rtmpdump4android;

import android.util.Log;

/**
 * 作者：Administrator on 2016/12/29/0029.
 * 邮箱：13163388931@qq.com
 * 说明:
 */
public class LogUtil {

    private static boolean isDebug = true;

    public final static String TAG = "LogUtils";

    public static void d(String content) {
        Log.d(TAG, content);
    }

    public static void e(String content) {
        Log.e(TAG, content);
    }

    public static void w(String content) {
        Log.w(TAG, content);
    }


    public static void d(String tag, String msg) {
        if (isDebug) {
            Log.d(tag, "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void d(Object object, String msg) {
        if (isDebug) {
            Log.d(object.getClass().getSimpleName(), "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void e(String tag, String msg) {
        if (isDebug) {
            Log.e(tag, "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void e(Object object, String msg) {
        if (isDebug) {
            Log.e(object.getClass().getSimpleName(), "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void i(String tag, String msg) {
        if (isDebug) {
            Log.i(tag, "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void i(Object object, String msg) {
        if (isDebug) {
            Log.i(object.getClass().getSimpleName(), "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void v(String tag, String msg) {
        if (isDebug) {
            Log.v(tag, "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void v(Object object, String msg) {
        if (isDebug) {
            Log.v(object.getClass().getSimpleName(), "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void w(String tag, String msg) {
        if (isDebug) {
            Log.w(tag, "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }

    public static void w(Object object, String msg) {
        if (isDebug) {
            Log.w(object.getClass().getSimpleName(), "thread-" + Thread.currentThread().getName() + "_" + Thread.currentThread().getId() + "   message- " + msg);
        }
    }
}
