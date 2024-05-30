package com.inseye.serviceclientdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class RedPointView extends View {
    private float x = 0;
    private float y = 0;
    private Paint paint;

    public RedPointView(Context context) {
        super(context);
        init();
    }

    public RedPointView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RedPointView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xFFFF0000); // Red color
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(x, y, 20, paint); // Draw a red point with radius 10
    }

    public void setPoint(float x, float y) {
        this.x = x;
        this.y = y;
        invalidate(); // Request to redraw the view
    }
}