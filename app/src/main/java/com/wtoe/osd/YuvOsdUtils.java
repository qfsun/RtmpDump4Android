package com.wtoe.osd;

public class YuvOsdUtils {

    static {
        System.loadLibrary("yuv-osd");
    }

    /**
     * 初始化时间水印
     *
     * @param osdOffX     水印在视频左上的x偏移
     * @param osdOffY     水印在视频左上的y 偏移
     * @param patternLen  水印格式长度
     * @param frameWidth  相机宽
     */
    public static native long initOsd(int osdOffX, int osdOffY, int patternLen, int frameWidth);

    /**
     * 释放内存
     */
    public static native void releaseOsd(long yuvHander);

    public static native void addOsd(long yuvHander ,byte[] yuvInData, byte[] outYvuData, String date);

}
