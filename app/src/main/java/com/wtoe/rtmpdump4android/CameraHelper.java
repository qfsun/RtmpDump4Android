package com.wtoe.rtmpdump4android;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.wtoe.libyuv.YuvUtils;
import com.wtoe.osd.YuvOsdUtils;
import com.wtoe.rtmp.RtmpHandle;
import com.wtoe.rtmpdump4android.flv.FlvPacker;
import com.wtoe.rtmpdump4android.flv.Packer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * android相机帮助类
 */
public class CameraHelper {
    private String TAG;

    private MediaCodec mEncoder;

    //采集到每帧数据时间
    private long previewTime = 0;
    //每帧开始编码时间
    private long encodeTime = 0;
    //每帧开始推送时间
    private long pushTime = 0;
    //采集数量
    private int count = 0;
    //编码数量
    private int encodeCount = 0;
    private int pushCount = 0;

    //编码类型
    private String mime = "video/avc";
    //pts时间基数
    private long presentationTimeUs = 0;

    private Camera mCamera;

    //相机ID
    private int m_CameraId;
    private int rotateDegree;

    private boolean isRunning;//是否还需要运行

    private int queueSize = 300;
    private ArrayBlockingQueue<byte[]> nv12Queue = new ArrayBlockingQueue<>(queueSize);

    private byte[] I420;

    private long yuvHander = 0;
    private long rtmpHandle = 0;

    private FlvPacker mFlvPacker;

    private ExecutorService pushExecutor = Executors.newSingleThreadExecutor();

    private String url;

    private boolean rtmp_Conn = false;//rtmp是否链接成功

    private FileChannel h264OutputStream;
    private FileOutputStream yuvOutputStream;

    /**
     * 设置参数
     */
    public CameraHelper(SurfaceView surfaceView, String rtmpUrl) {
        this.m_CameraId = Constants.VIDEO_CAMERA_ID;
        this.url = rtmpUrl;
        I420 = new byte[Constants.VIDEO_WIDTH * Constants.VIDEO_HEIGHT * 3 / 2];
        if (m_CameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//            前置摄像头需要旋转270°
            rotateDegree = 270;
        } else {
//            后置摄像头需要旋转90°
            rotateDegree = 90;
        }
        this.TAG = CameraHelper.class.getSimpleName();
        //得到视频数据回调类
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopPreview();
                destroyCamera();
            }
        });
        createCamera(surfaceView.getHolder());
        initMediaCodec();
        startPreview();
    }

    private ExecutorService encodeExecutor = Executors.newSingleThreadExecutor();

    private byte[] outData;
    private SimpleDateFormat mFormat;

    /**
     * 开启预览
     */
    private synchronized void startPreview() {
        if (mCamera != null && !isRunning) {
            LogUtil.d(TAG, "startPreview");
            isRunning = true;
            startRtmpPush();
//            String pattern = "yyyy年MM月dd日 HH:mm:ss";//日期格式 年月日
            String pattern = "yyyy-MM-dd HH:mm:ss";//日期格式
            mFormat = new SimpleDateFormat(pattern, Locale.CHINA);
            yuvHander = 0;
            yuvHander = YuvOsdUtils.initOsd(20, 50, pattern.length(), Constants.VIDEO_HEIGHT);

            int previewFormat = mCamera.getParameters().getPreviewFormat();
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            int size = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewFormat) / 8;
            mCamera.addCallbackBuffer(new byte[size]);
            // Camera  采集信息回调
            // TODO: 17/6/15 获取到数据的格式？ YUV？支持的分辨率？
            mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(final byte[] data, Camera camera) {
                    long endTime = System.currentTimeMillis();
                    encodeExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            encodeTime = System.currentTimeMillis();
                            if (outData == null) {
                                outData = new byte[data.length];
                            }
//                            NV21转I420，镜像、旋转90度
                            YuvUtils.compressYUV(data, Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT, I420, Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT, 0, rotateDegree, false);
//                            outData = I420;
//                            if (yuvHander != 0) {
////                                添加水印
//                                String date = mFormat.format(new Date());
//                                YuvOsdUtils.addOsd(yuvHander, I420, outData, date);
//                            }
//                            try {
//                                if (yuvOutputStream == null) {
//                                    File file = new File(Environment.getExternalStorageDirectory(), "yuvFile.yuv");//"/mnt/sdcard"会根据版本兼容问题
//                                    yuvOutputStream = new FileOutputStream(file);
//                                }
//                                yuvOutputStream.write(outData);
//                            }catch (Exception e){
//                                e.printStackTrace();
//                            }
//                            addDataToQueue(outData);
                            addDataToQueue(I420);
                            encodeCount++;
                            if (encodeCount % 50 == 1) {
                                LogUtil.w("编码第:" + encodeCount + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
                            }
                        }
                    });
                    mCamera.addCallbackBuffer(data);
                    count++;
                    if (count % 50 == 1) {
                        LogUtil.d("采集第:" + (count) + "帧，距上一帧间隔时间:" + (endTime - previewTime) + "  " + Thread.currentThread().getName());
                    }
                    previewTime = endTime;
                }
            });
        }
    }

    private void addDataToQueue(byte[] data) {
        if (nv12Queue == null) {
            return;
        }
        if (nv12Queue.size() >= queueSize) {
            nv12Queue.poll();
            LogUtil.e(TAG, "lostPacket");
        }
        nv12Queue.add(data);
    }

    private MediaCodec.Callback encodeCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            try {
                byte[] data = nv12Queue.poll();
                ByteBuffer inputBuffer = codec.getInputBuffer(index);
                inputBuffer.clear();
                long pts = 0;
                int dataSize = 0;
                if (data != null) {
                    //计算pts，这个值是一定要设置的
                    pts = computePresentationTime(presentationTimeUs);
                    inputBuffer.put(data);
                    presentationTimeUs += 1;
                    dataSize = data.length;
                }
                codec.queueInputBuffer(index, 0, dataSize, pts, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            try {
                //编码器输出缓冲区
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);

//                if (h264OutputStream == null) {
//                    File file = new File(Environment.getExternalStorageDirectory(), "h264File.h264");//"/mnt/sdcard"会根据版本兼容问题
//                    h264OutputStream = new FileOutputStream(file, true).getChannel();
//                }
//                h264OutputStream.write(outputBuffer);
                //进行flv封装
                mFlvPacker.onVideoData(outputBuffer, info);
                codec.releaseOutputBuffer(index, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, "Output onError !");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.e(TAG, "onOutputFormatChanged !");
        }
    };

    /**
     * 开启摄像头
     *
     * @return
     */
    private boolean createCamera(SurfaceHolder surfaceHolder) {
        try {
            mCamera = Camera.open(m_CameraId);
            Camera.Parameters parameters = mCamera.getParameters();
            //设置预览帧率
            parameters.setPreviewFrameRate(Constants.VIDEO_FRAMERATE);
            mCamera.setDisplayOrientation(90);
            Camera.CameraInfo camInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(m_CameraId, camInfo);
            parameters.setPictureFormat(ImageFormat.NV21);
            parameters.setPreviewFormat(ImageFormat.NV21);
            Camera.Size size = getBestPreviewSize(Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT, parameters);
            if (size != null) {
                Constants.VIDEO_WIDTH = size.width;
                Constants.VIDEO_HEIGHT = size.height;
                //设置预览图像分辨率
                parameters.setPreviewSize(size.width, size.height);
                parameters.setPictureSize(size.width, size.height);
            }
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video")) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            if (mFlvPacker == null) {
                mFlvPacker = new FlvPacker();
                mFlvPacker.initVideoParams(Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT, Constants.VIDEO_FRAMERATE);
                mFlvPacker.setPacketListener(new Packer.OnPacketListener() {
                    @Override
                    public void onPacket(final byte[] data, final int packetType) {
                        pushExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                pushTime = System.currentTimeMillis();
                                pushCount++;
                                if (pushCount % 50 == 1) {
                                    LogUtil.w("FlvPacker onPacket");
                                }
                                if (rtmp_Conn) {
                                    int ret = RtmpHandle.getInstance().pushFlvData(rtmpHandle, data, data.length);
                                    if (encodeCount % 50 == 1) {
                                        LogUtil.w("推送第:" + (encodeCount++) + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
                                        LogUtil.w("推送RTMP  type：" + packetType + "  length:" + data.length + "  推流结果:" + ret);
                                    }
                                }
                            }
                        });
                    }
                });
            }
            mCamera.setParameters(parameters);
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
            return true;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            destroyCamera();
            e.printStackTrace();
            return false;
        }
    }

    private void startRtmpPush() {
        mFlvPacker.start();
        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {
                rtmpHandle = RtmpHandle.getInstance().initRtmpHandle(url);
                if (rtmpHandle != 0) {
                    rtmp_Conn = true;
                } else {
                    LogUtil.w("RTMP连接失败: " + rtmpHandle);
                }
            }
        });
    }

    /**
     * 初始化 MediaCodec 编码器
     */
    private void initMediaCodec() {
        try {
            LogUtil.d(TAG, "initMediaCodec");
            mEncoder = MediaCodec.createEncoderByType(mime);
            if (Constants.VIDEO_BITRATE == 0) {
                Constants.VIDEO_BITRATE = 2 * Constants.VIDEO_WIDTH * Constants.VIDEO_HEIGHT * Constants.VIDEO_FRAMERATE / 20;
            }

            MediaFormat mediaFormat;
//            旋转90度，这里的宽高就要变换一下
            mediaFormat = MediaFormat.createVideoFormat(mime, Constants.VIDEO_HEIGHT, Constants.VIDEO_WIDTH);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, Constants.VIDEO_BITRATE);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, Constants.VIDEO_FRAMERATE);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

            mEncoder.setCallback(encodeCallback);
            mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    //通过mimeType确定支持的格式
    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    /**
     * 计算视频pts
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / Constants.VIDEO_FRAMERATE;
    }

    /**
     * 计算相机支持的 Framerate
     *
     * @param parameters 相机属性参数
     * @return
     */
    private int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        //获取相机硬件支持的Fps范围参数
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        //遍历获取
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            System.out.println("帧率：" + Arrays.toString(interval));
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        return maxFps;
    }

    /**
     * 根据宽高，选择最合适的尺寸
     *
     * @param width
     * @param height
     * @param parameters
     * @return
     */
    private Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            System.out.println(" .width：" + size.width + ".height：" + size.height);
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) {
                        result = size;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 停止预览
     */
    private synchronized void stopPreview() {
        LogUtil.e(TAG, "--stopPreview--\r\n");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
        }
    }

    /**
     * 销毁Camera
     */
    private synchronized void destroyCamera() {
        LogUtil.e(TAG, "--destroyCamera--\r\n");
        try {
            if (null != mCamera) {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                try {
                    mCamera.release();
                } catch (Exception e) {
                }
                mCamera = null;
            }
            if (encodeExecutor != null) {
                encodeExecutor.shutdown();
            }
            if (mFlvPacker != null) {
                mFlvPacker.stop();
                mFlvPacker = null;
            }
            if (yuvHander != 0) {
                YuvOsdUtils.releaseOsd(yuvHander);
                yuvHander = 0;
            }
            if (rtmpHandle != 0) {
                RtmpHandle.getInstance().releaseRtmpHandle(rtmpHandle);
                rtmpHandle = 0;
            }
            isRunning = false;
            if (h264OutputStream != null) {
                h264OutputStream.close();
                h264OutputStream = null;
            }
            if (yuvOutputStream != null) {
                yuvOutputStream.close();
                yuvOutputStream = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 释放资源
     */
    public void onDestroy() {
        destroyCamera();
        try {
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
                mEncoder = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
