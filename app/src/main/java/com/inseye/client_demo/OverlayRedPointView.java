package com.inseye.client_demo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.inseye.sdk.ScreenUtils;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.LinkedList;
import java.util.Queue;

public class OverlayRedPointView extends FrameLayout {

    private Vector2D redPoint = new Vector2D(0, 0);
    private Paint gazePaint;
    private Paint trailPaint;


    private final Queue<PointF> trailPoints = new LinkedList<>();
    private static final int MAX_TRAIL_POINTS = 100; // Adjust as needed

    public OverlayRedPointView(Context context) {
        super(context);
        init();
    }

    public OverlayRedPointView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlayRedPointView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gazePaint = new Paint();
        gazePaint.setColor(0xFFFF0000);
        gazePaint.setStyle(Paint.Style.FILL);

        int trailColor = ContextCompat.getColor(getContext(), R.color.trail);
        trailPaint = new Paint();
        trailPaint.setColor(trailColor);
        trailPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false); // This is important to ensure that onDraw gets called
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        // Add the current point to the trail
        trailPoints.offer(new PointF((float) redPoint.getX(), (float) redPoint.getY()));
        if (trailPoints.size() > MAX_TRAIL_POINTS) {
            trailPoints.poll(); // Remove oldest point if exceeding the limit
        }

        for (PointF point : trailPoints) {
            canvas.drawCircle(point.x, point.y, 5, trailPaint);
        }

        // Draw the current red point
        canvas.drawCircle((float) redPoint.getX(), (float) redPoint.getY(), 20, gazePaint);

    }

    public void setPoint(Vector2D position) {
        this.redPoint = position;
        invalidate(); // Request to redraw the view
    }
}