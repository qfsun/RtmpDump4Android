# This is the Android makefile for libyuv for both platform and NDK.
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CPP_EXTENSION := .cc

LOCAL_SRC_FILES := \
    librtmp/amf.c           \
    librtmp/hashswf.c    \
    librtmp/log.c           \
    librtmp/parseurl.c      \
    librtmp/rtmp.c

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_MODULE := librtmp
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := rtmp-push
LOCAL_SRC_FILES := RtmpHandle.cpp  RtmpUtil.cpp
LOCAL_STATIC_LIBRARIES := librtmp
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)


