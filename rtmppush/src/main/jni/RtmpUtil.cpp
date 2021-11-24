#include <RtmpUtil.h>
/*read 1 byte*/
int ReadU8(uint32_t *u8, FILE *fp) {
    if (fread(u8, 1, 1, fp) != 1)
        return 0;
    return 1;
}

/*read 2 byte*/
int ReadU16(uint32_t *u16, FILE *fp) {
    if (fread(u16, 2, 1, fp) != 1)
        return 0;
    *u16 = HTON16(*u16);
    return 1;
}

/*read 3 byte*/
int ReadU24(uint32_t *u24, FILE *fp) {
    if (fread(u24, 3, 1, fp) != 1)
        return 0;
    *u24 = HTON24(*u24);
    return 1;
}

/*read 4 byte*/
int ReadU32(uint32_t *u32, FILE *fp) {
    if (fread(u32, 4, 1, fp) != 1)
        return 0;
    *u32 = HTON32(*u32);
    return 1;
}

/*read 1 byte,and loopback 1 byte at once*/
int PeekU8(uint32_t *u8, FILE *fp) {
    if (fread(u8, 1, 1, fp) != 1)
        return 0;
    fseek(fp, -1, SEEK_CUR);
    return 1;
}

/*read 4 byte and convert to time format*/
int ReadTime(uint32_t *utime, FILE *fp) {
    if (fread(utime, 4, 1, fp) != 1)
        return 0;
    *utime = HTONTIME(*utime);
    return 1;
}

/*
 * ===========================================================================
 *      使用RTMPDump进行Flv文件推流
 * ===========================================================================
 */
int RtmpUtils::pushFlvFile(const char *url, const char *path) {
    logw("pushFlvFile");
    RTMP *rtmp = NULL;
    RTMPPacket *packet = NULL;
    uint32_t start_time = 0;
    //the timestamp of the previous frame
    long pre_frame_time = 0;
    int bNextIsKey = 1;
    uint32_t preTagsize = 0;
    //packet attributes
    uint32_t type = 0;
    uint32_t datalength = 0;
    uint32_t timestamp = 0;
    uint32_t streamid = 0;

    FILE *fp = NULL;
    fp = fopen(path, "rb");
    if (!fp) {
        RTMP_LogPrintf("Open File Error.\n");
        loge("Open File Error.\n");
        return -1;
    }

    rtmp = RTMP_Alloc();
    RTMP_Init(rtmp);
    //set connection timeout,default 30s
    rtmp->Link.timeout = 5;
    if (!RTMP_SetupURL(rtmp, const_cast<char *>(url))) {
        RTMP_Log(RTMP_LOGERROR, "SetupURL Error\n");
        loge("SetupURL Error.\n");
        RTMP_Free(rtmp);
        return -1;
    }

    //if unable,the AMF command would be 'play' instead of 'publish'
    RTMP_EnableWrite(rtmp);

    if (!RTMP_Connect(rtmp, NULL)) {
        RTMP_Log(RTMP_LOGERROR, "Connect Error\n");
        loge("Connect Error.\n");
        RTMP_Free(rtmp);
        return -1;
    }

    if (!RTMP_ConnectStream(rtmp, 0)) {
        RTMP_Log(RTMP_LOGERROR, "ConnectStream Err\n");
        loge("ConnectStream Error.\n");
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        return -1;
    }

    packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, 1024 * 64);
    RTMPPacket_Reset(packet);

    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    RTMP_LogPrintf("Start to send data ...\n");

    //jump over FLV Header
    fseek(fp, 9, SEEK_SET);
    //jump over previousTagSizen
    fseek(fp, 4, SEEK_CUR);
    start_time = RTMP_GetTime();
    while (1) {
        //not quite the same as FLV spec
        if (!ReadU8(&type, fp))
            break;
        if (!ReadU24(&datalength, fp))
            break;
        if (!ReadTime(&timestamp, fp))
            break;
        if (!ReadU24(&streamid, fp))
            break;

        if (type != 0x08 && type != 0x09) {
            //jump over non_audio and non_video frame，
            //jump over next previousTagSizen at the same time
            fseek(fp, datalength + 4, SEEK_CUR);
            continue;
        }

        if (fread(packet->m_body, 1, datalength, fp) != datalength)
            break;

        packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
        packet->m_nTimeStamp = timestamp;
        packet->m_packetType = type;
        packet->m_nBodySize = datalength;
        pre_frame_time = timestamp;
        long delt = RTMP_GetTime() - start_time;
        printf("%ld,%ld\n", pre_frame_time, static_cast<long>(RTMP_GetTime() - start_time));
        __android_log_print(ANDROID_LOG_WARN, " wtoe JNI ",
                            "%ld,%ld", pre_frame_time,
                            static_cast<long>(RTMP_GetTime() - start_time));
        if (delt < pre_frame_time) {
            usleep((pre_frame_time - delt) * 1000);
        }
        if (!RTMP_IsConnected(rtmp)) {
            RTMP_Log(RTMP_LOGERROR, "rtmp is not connect\n");
            loge("rtmp is not connect.\n");
            break;
        }
        if (!RTMP_SendPacket(rtmp, packet, 0)) {
            RTMP_Log(RTMP_LOGERROR, "Send Error\n");
            loge("Send Error.\n");
            break;
        }

        if (!ReadU32(&preTagsize, fp))
            break;

        if (!PeekU8(&type, fp))
            break;
        if (type == 0x09) {
            if (fseek(fp, 11, SEEK_CUR) != 0)
                break;
            if (!PeekU8(&type, fp)) {
                break;
            }
            if (type == 0x17)
                bNextIsKey = 1;
            else
                bNextIsKey = 0;

            fseek(fp, -11, SEEK_CUR);
        }
    }

    RTMP_LogPrintf("\nSend Data Over\n");
    logd("\nSend Data Over.\n");
    if (fp)
        fclose(fp);

    if (rtmp != NULL) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = NULL;
    }
    if (packet != NULL) {
        RTMPPacket_Free(packet);
        free(packet);
        packet = NULL;
    }
    return 0;
}


/*
 * ===========================================================================
 *      MediaCodec编码数据使用RTMPDump进行推流
 * ===========================================================================
 */

/*
  * 初始化RTMP连接
 */
int RtmpUtils::initRtmpHandle(const char *url) {
    //    如果已经初始化，多次调用，则直接返回
    if (is_init) {
        loge("RtmpHandle Already init.");
        return -4;
    }

    size_t len = strlen(url);

    char *rtmpUrl = NULL;
    rtmpUrl = (char *) malloc(len + 1);
    memset(rtmpUrl, 0, len + 1);
    memcpy(rtmpUrl, url, len);

    rtmp = RTMP_Alloc();
    RTMP_Init(rtmp);
    //set connection timeout,default 30s
    rtmp->Link.timeout = 5;
    if (!RTMP_SetupURL(rtmp, rtmpUrl)) {
        RTMP_Log(RTMP_LOGERROR, "SetupURL Error\n");
        loge("SetupURL Err\n");
        RTMP_Free(rtmp);
        return -1;
    }
    logw("RTMP_SetupURL");

    //if unable,the AMF command would be 'play' instead of 'publish'
    RTMP_EnableWrite(rtmp);
    logw("RTMP_EnableWrite");
    if (!RTMP_Connect(rtmp, NULL)) {
        RTMP_Log(RTMP_LOGERROR, "RTMP_Connect Error\n");
        loge("RTMP_Connect Err\n");
        RTMP_Free(rtmp);
        return -2;
    }
    logw("RTMP_Connect");

    if (!RTMP_ConnectStream(rtmp, 0)) {
        RTMP_Log(RTMP_LOGERROR, "ConnectStream Err\n");
        loge("ConnectStream Err\n");
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        return -3;
    }
    logw("RTMP_ConnectStream");

    packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, 1024 * 512);
    RTMPPacket_Reset(packet);
    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    logd("Ready to send data ...");
    RTMP_LogPrintf("Ready to send data ...\n");
    is_init = true;
    return 0;
}

int RtmpUtils::pushFlvData(unsigned char *buf, jint length) {
    // TODO
    if (length < 15) {
        return -1;
    }
    //packet attributes
    uint32_t type = 0;
    uint32_t dataLength = 0;
    uint32_t timeStamp = 0;
    uint32_t streamId = 0;

    memcpy(&type, buf, 1);
    buf++;
    memcpy(&dataLength, buf, 3);
    dataLength = HTON24(dataLength);
    buf += 3;
    memcpy(&timeStamp, buf, 4);
    timeStamp = HTONTIME(timeStamp);
    buf += 4;
    memcpy(&streamId, buf, 3);
    streamId = HTON24(streamId);
    buf += 3;
    if(mCount % 50 == 0) {
        mCount = 0;
        __android_log_print(ANDROID_LOG_WARN, "WTOE PUSH_RTMP","解析包数据：%u,%u,%u,%u,%d",type, dataLength, timeStamp, streamId, length);
    }

    mCount++;

    if (type != 0x08 && type != 0x09) {
        return -2;
    }

    if (dataLength != (length - 11 - 4)) {
        return -3;
    }

    memcpy(packet->m_body, buf, dataLength+11+4);

    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = timeStamp;
    packet->m_packetType = type;
    packet->m_nBodySize = dataLength;
    if (!RTMP_IsConnected(rtmp)) {
        RTMP_Log(RTMP_LOGERROR, "rtmp is not connect\n");
        return -4;
    }
    if (!RTMP_SendPacket(rtmp, packet, 0)) {
        RTMP_Log(RTMP_LOGERROR, "Send Error\n");
        return -5;
    }
    return 0;
}

void RtmpUtils::releaseRtmpHandle() {
    logd("JNI Rtmp close\n");
    if (!is_init) {
        loge("releaseRtmpHandle . is_init = false");
        return;
    }
    if (rtmp != NULL) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = NULL;
    }
    if (packet != NULL) {
        RTMPPacket_Free(packet);
        free(packet);
        packet = NULL;
    }
    is_init = false;
}