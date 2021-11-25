# RtmpDump4Android
Android采集相机数据，MediaCodec编码H264，封装flv格式，基于rtmpdump推送rtmp数据至流媒体服务器

【osd】
jni库，是基于i420数据添加时间水印，有已编译的so文件，源码。

【yuv】
jni库，基于libyuv，将nv21数据转nv12，有已编译的so文件，源码。

【rtmp】
jni库，基于rtmpdump，实现推送flv文件，以及封装flv格式的一帧数据，有已编译的so文件，源码。
