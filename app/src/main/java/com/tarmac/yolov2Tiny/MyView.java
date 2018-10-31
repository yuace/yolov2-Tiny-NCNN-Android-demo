package com.tarmac.yolov2Tiny;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class MyView extends View {

    private float l;
    private float t;
    private float r;
    private float b;

    public void setRect(float l, float t, float r, float b) {
        this.l = l;
        this.t = t;
        this.r = r;
        this.b = b;
    }

    public MyView(Context context) {
        super(context);
    }

    public MyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        RectF rectF = new RectF(l, t, r, b);
        canvas.drawRect(rectF, paint);
    }
}
