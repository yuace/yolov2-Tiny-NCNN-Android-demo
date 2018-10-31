package com.tarmac.yolov2Tiny;
import android.graphics.Bitmap;

public class yolov2Tiny {
    public native boolean Init(byte[] param, byte[] bin);
    public native float[] Detect(Bitmap bitmap);
    static {
        System.loadLibrary("yolov2_jni");
    }

}
