package com.inseye.serviceclientdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

public class PointView extends View {
    private Paint paint;
    private float x = -1;
    private float y = -1;

    public PointView(Context context) {
        super(context);
        init();
    }

    public PointView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PointView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED); // Set default color to black
        paint.setStrokeWidth(10); // Set default stroke width
    }

    public void setPoint(float x, float y) {
        this.x = x;
        this.y = y;
        invalidate(); // Request to redraw the view
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (x != -1 && y != -1) {
            canvas.drawPoint(x, y, paint);
        }
    }
}
