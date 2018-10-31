package com.tarmac.yolov2Tiny;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getName();
    private static final int USE_PHOTO = 1001;
    private String camera_image_path;
    private ImageView show_image;
    private TextView result_text;
    private boolean load_result = false;
    private int[] ddims = {1, 3, 416, 416};
    private int model_index = 1;
    private List<String> resultLabel = new ArrayList<>();
    private yolov2Tiny yolov2Tiny = new yolov2Tiny();
    private MyView myView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myView = findViewById(R.id.myview);
        try {
            initYolov2Tiny();
        } catch (IOException e) {
            Log.e("MainActivity", "initYolov2Tiny error");
        }
        init_view();
        readCacheLabelFromLocalFile();
    }

    private void initYolov2Tiny() throws IOException {
        byte[] param = null;
        byte[] bin = null;
        {
            InputStream assetsInputStream = getAssets().open("yolov2-tiny_tarmac.param.bin");
            int available = assetsInputStream.available();
            param = new byte[available];
            int byteCode = assetsInputStream.read(param);
            assetsInputStream.close();
        }
        {
            InputStream assetsInputStream = getAssets().open("yolov2-tiny_tarmac1.bin");
            int available = assetsInputStream.available();
            bin = new byte[available];
            int byteCode = assetsInputStream.read(bin);
            assetsInputStream.close();
        }

        load_result = yolov2Tiny.Init(param, bin);
        Log.d("load model", "yolov2tiny_load_model_result:" + load_result);
    }

    // initialize view
    private void init_view() {
        request_permissions();
        show_image = (ImageView) findViewById(R.id.show_image);
        result_text = (TextView) findViewById(R.id.result_text);
        result_text.setMovementMethod(ScrollingMovementMethod.getInstance());
        Button use_photo = (Button) findViewById(R.id.use_photo);
        // use photo click
         use_photo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!load_result) {
                    Toast.makeText(MainActivity.this, "never load model", Toast.LENGTH_SHORT).show();
                    return;
                }
                PhotoUtil.use_photo(MainActivity.this, USE_PHOTO);
            }
        });
    }

    // load label's name
    private void readCacheLabelFromLocalFile() {
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("words.txt")));
            String readLine = null;
            while ((readLine = reader.readLine()) != null) {
                resultLabel.add(readLine);
            }
            reader.close();
        } catch (Exception e) {
            Log.e("labelCache", "error " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        String image_path;
        RequestOptions options = new RequestOptions().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case USE_PHOTO:
                    if (data == null) {
                        Log.w(TAG, "user photo data is null");
                        return;
                    }
                    Uri image_uri = data.getData();

                    //Glide.with(MainActivity.this).load(image_uri).apply(options).into(show_image);

                    // get image path from uri
                    image_path = PhotoUtil.get_path_from_URI(MainActivity.this, image_uri);
                    // predict image
                    predict_image(image_path);
                    break;
            }
        }
    }

    //  predict image
    private void predict_image(String image_path) {
        // picture to float array
        Bitmap bmp = PhotoUtil.getScaleBitmap(image_path);
        Bitmap rgba = bmp.copy(Bitmap.Config.ARGB_8888, true);
        // resize to 416x416
        Bitmap input_bmp = Bitmap.createScaledBitmap(rgba, ddims[2], ddims[3], false);
        try {
            // Data format conversion takes too long
            // Log.d("inputData", Arrays.toString(inputData));
            long start = System.currentTimeMillis();
            // get predict result
            float[] result = yolov2Tiny.Detect(input_bmp);
            long end = System.currentTimeMillis();
            Log.d(TAG, "origin predict result:" + Arrays.toString(result));
            long time = end - start;
            Log.d("result length", "length of result: " + String.valueOf(result.length));
            // show predict result and time
            float[] r = get_max_result(result);

            String show_text = "result：" + Arrays.toString(r) + "\nname：" + resultLabel.get((int) r[0]) + "\nprobability：" + r[1] + "\ntime：" + time + "ms" ;
            result_text.setText(show_text);
            Canvas canvas = new Canvas(rgba);
            //图像上画矩形
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);//不填充
            paint.setStrokeWidth(10); //线的宽度
            canvas.drawRect(r[2]*rgba.getWidth(), r[3]*rgba.getHeight(), r[4]*rgba.getWidth(), r[5]*rgba.getHeight(), paint);
            show_image.setImageBitmap(rgba);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // get max probability label
    private float[] get_max_result(float[] result) {
        int num_rs = result.length / 6;
        float maxProp = result[1];
        int maxI = 0;
        for(int i = 1; i<num_rs;i++){
            if(maxProp<result[i*6+1]){
                maxProp = result[i*6+1];
                maxI = i;
            }
        }
        float[] ret = {0,0,0,0,0,0};
        for(int j=0;j<6;j++){
            ret[j] = result[maxI*6 + j];
        }
        return ret;
    }

    // request permissions
    private void request_permissions() {
        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        // if list is not empty will request permissions
        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[permissionList.size()]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        int grantResult = grantResults[i]; 
                        if (grantResult == PackageManager.PERMISSION_DENIED) {
                            String s = permissions[i];
                            Toast.makeText(this, s + "permission was denied", Toast.LENGTH_SHORT).show();
                        }
                    } 
                } 
                break;
        }
    }



}
