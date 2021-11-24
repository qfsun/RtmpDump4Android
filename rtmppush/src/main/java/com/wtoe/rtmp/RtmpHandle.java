package com.wtoe.rtmp;

/**
 * Desc :  RTMPDump调用类
 */

public class RtmpHandle {
    private static RtmpHandle mInstance;

    private RtmpHandle() {

    }

    public synchronized static RtmpHandle getInstance() {
        if (mInstance == null) {
            mInstance = new RtmpHandle();
        }
        return mInstance;
    }

    static {
        System.loadLibrary("rtmp");
        System.loadLibrary("rtmp-push");
    }


    public native long initRtmpHandle(String url);

    public native int pushFlvData(long rtmpHander,byte[] buf, int length);

    public native int pushFlvFile(String url, String filePath);

    public native void releaseRtmpHandle(long rtmpHander);
}
