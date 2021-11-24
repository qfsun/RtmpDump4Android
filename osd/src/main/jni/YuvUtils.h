#include <jni.h>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <sys/time.h>

#ifndef JRTPLIB_YUVUTILS_H
#define JRTPLIB_YUVUTILS_H
#ifdef __cplusplus
extern "C" {
#endif

class YuvUtils {
public:
    void initOsd(jint osdOffX, jint osdOffY, jint patternLen, jint frameWidth);
    void addOsd(JNIEnv *env,jbyteArray yuv_in_data, jbyteArray yvu_out_data,jstring date_);
    void releaseOsd();

private:
    int off_x, off_y;//x 偏移y 偏移
    jint num_width, num_height;//数字宽高
    int date_len;
    int frame_width;
    char *mNumArrays;
    size_t size;
    bool is_init;//初始化标识
};

#ifdef __cplusplus
}
#endif
#endif //JRTPLIB_YUVUTILS_H
