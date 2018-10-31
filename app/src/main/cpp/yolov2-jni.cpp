#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
// ncnn
#include "include/net.h"
#include "include/opencv.h"
#include "yolov2-tiny_tarmac.id.h"
#include <sys/time.h>
#include <unistd.h>
static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;

static ncnn::Mat ncnn_param;
static ncnn::Mat ncnn_bin;
static ncnn::Net ncnn_net;

extern "C" {

// public native boolean Init(byte[] words,byte[] param, byte[] bin);
JNIEXPORT jboolean JNICALL
Java_com_tarmac_yolov2Tiny_yolov2Tiny_Init(JNIEnv *env, jobject obj, jbyteArray param, jbyteArray bin) {
    __android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJni", "enter the jni func");
    // init param
    {
        int len = env->GetArrayLength(param);
        ncnn_param.create(len, (size_t) 1u);
        env->GetByteArrayRegion(param, 0, len, (jbyte *) ncnn_param);
        int ret = ncnn_net.load_param((const unsigned char *) ncnn_param);
        __android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJni", "load_param %d %d", ret, len);
    }

    // init bin
    {
        int len = env->GetArrayLength(bin);
        ncnn_bin.create(len, (size_t) 1u);
        env->GetByteArrayRegion(bin, 0, len, (jbyte *) ncnn_bin);
        int ret = ncnn_net.load_model((const unsigned char *) ncnn_bin);
        __android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJni", "load_model %d %d", ret, len);
    }

    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;

    ncnn::set_default_option(opt);

    return JNI_TRUE;
}

// public native String Detect(Bitmap bitmap);
JNIEXPORT jfloatArray JNICALL Java_com_tarmac_yolov2Tiny_yolov2Tiny_Detect(JNIEnv* env, jobject thiz, jobject bitmap)
{
    // ncnn from bitmap
    ncnn::Mat in;
    {
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, bitmap, &info);
//        int origin_w = info.width;
//        int origin_h = info.height;
//        int width = 416;
//        int height = 416;
        int width = info.width;
        int height = info.height;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
            return NULL;

        void* indata;
        AndroidBitmap_lockPixels(env, bitmap, &indata);
        // 把像素转换成data，并指定通道顺序
//        in = ncnn::Mat::from_pixels_resize((const unsigned char*)indata, ncnn::Mat::PIXEL_RGBA2RGB, origin_w, origin_h, width, height);

        in = ncnn::Mat::from_pixels((const unsigned char*)indata, ncnn::Mat::PIXEL_RGBA2RGB, width, height);
        //__android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJniIn", "yolov2_predict_has_input1, in.w: %d; in.h: %d", in.w, in.h);
        AndroidBitmap_unlockPixels(env, bitmap);
    }

    // ncnn_net
    std::vector<float> cls_scores;
    {
        // 减去均值和乘上比例
        const float mean_vals[3] = {0.5f, 0.5f, 0.5f};
        const float scale[3] = {0.007843f, 0.007843f, 0.007843f};
        //__android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJniIn", "yolov2_predict_has_input2, in[0][0]: %f; in[250][250]: %f", in.row(0)[0], in.row(250)[250]);
//        in.substract_mean_normalize(mean_vals, scale);
        in.substract_mean_normalize(mean_vals, scale);
        //__android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJniIn", "yolov2_predict_has_input3, in[0][0]: %f; in[250][250]: %f", in.row(0)[0], in.row(250)[250]);

        ncnn::Extractor ex = ncnn_net.create_extractor();
        // 如果时不加密是使用ex.input("data", in);
//        ex.input(mobilenet_v2_param_id::BLOB_data, in);
        ex.input(yolov2_tiny_tarmac_param_id::BLOB_data, in);
        //ex.input("data",in);
        ncnn::Mat out;
        // 如果时不加密是使用ex.extract("prob", out);
        //ex.extract(mobilenet_v2_param_id::BLOB_prob, out);
        ex.extract(yolov2_tiny_tarmac_param_id::BLOB_detection_out, out);
//        ex.extract("detection-out",out);
//        int output_wsize = in.w;
//        int output_hsize = in.h;
//        jint *in_out[output_wsize * output_hsize * in.c];
//        for(int i=0; i<output_wsize * output_hsize * in.c;i++){
//            in_out[i] = (int *)&in[i];
//        }
//        jintArray  jOutImData = env->NewIntArray(output_hsize * output_wsize * in.c);
//        env->SetIntArrayRegion(jOutImData, 0, output_hsize * output_wsize * in.c, reinterpret_cast<const jint *>(*in_out));
        //__android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJniOut", "yolov2_predict_has_outcome, out.w: %d; out.h: %d", out.w, out.h);
        //__android_log_print(ANDROID_LOG_DEBUG, "yolov2TinyJniOut", "yolov2_predict_has_outcome_data, out[0][2]: %f;", out.row(0)[2]);
        int output_wsize = out.w;
        int output_hsize = out.h;

        jfloat *output[output_wsize * output_hsize];
        for(int i = 0; i< out.h; i++) {
            for (int j = 0; j < out.w; j++) {
                output[i*output_wsize + j] = &out.row(i)[j];
            }
        }
        jfloatArray jOutputData = env->NewFloatArray(output_wsize);
        if (jOutputData == nullptr) return nullptr;
        env->SetFloatArrayRegion(jOutputData, 0,  output_wsize * output_hsize,
                                 reinterpret_cast<const jfloat *>(*output));  // copy

        return jOutputData;
    }
}
}
