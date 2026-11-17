package com.obddroid.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;
import com.obddroid.utils.DebugLog;

/**
 * Custom view that displays a tilt indicator (inclinometer) for vehicle motion
 * Shows a ball that moves based on X/Y acceleration to indicate vehicle tilt
 * Uses phone's accelerometer sensor for real-time updates
 */
public class TiltIndicatorView extends View implements SensorEventListener {

    private Paint circlePaint;
    private Paint centerCirclePaint;
    private Paint ballPaint;
    private Paint textPaint;

    private float accelX = 0.0f;  // m/s² (left/right tilt)
    private float accelY = 0.0f;  // m/s² (forward/backward tilt)

    // Calibration offsets - set when user taps to recalibrate
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    public TiltIndicatorView(Context context) {
        super(context);
        init();
    }

    public TiltIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TiltIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize sensor manager
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Set up tap listener to recalibrate
        setOnClickListener(v -> recalibrate());
        setClickable(true);

        // Outer circle paint (boundary)
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(2f);
        circlePaint.setColor(0xFFAAAAAA);  // Light gray - visible on dark background

        // Center reference circle paint
        centerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerCirclePaint.setStyle(Paint.Style.STROKE);
        centerCirclePaint.setStrokeWidth(1.5f);
        centerCirclePaint.setColor(0xFF888888);  // Medium-light gray

        // Ball paint (moves with tilt)
        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaint.setStyle(Paint.Style.FILL);
        ballPaint.setColor(0xFF00BCD4);  // Cyan color

        // Text paint for label
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(24f);
        textPaint.setColor(0xFFFFFFFF);  // White
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Register sensor listener when view is attached
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Unregister sensor listener when view is detached to save battery
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Update acceleration values
            // X axis: positive = device tilted right, negative = left
            // Y axis: positive = device tilted down (towards user), negative = up (away from user)
            accelX = event.values[0];
            accelY = event.values[1];
            invalidate();  // Redraw the view
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this use case
    }

    /**
     * Recalibrate the tilt indicator - current position becomes the new center/zero point
     */
    private void recalibrate() {
        offsetX = accelX;
        offsetY = accelY;
        invalidate();
        DebugLog.d("TiltIndicatorView", String.format("Recalibrated: offsetX=%f, offsetY=%f", offsetX, offsetY));
    }

    /**
     * Update the acceleration values (in m/s²)
     * @param x X-axis acceleration (left/right)
     * @param y Y-axis acceleration (forward/backward)
     */
    public void setAcceleration(float x, float y) {
        this.accelX = x;
        this.accelY = y;
        invalidate();  // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float centerX = width / 2f;
        float centerY = height / 2f;

        // Calculate the radius for the indicator (leave some padding)
        float maxRadius = Math.min(width, height) / 2f - 20f;

        // Draw concentric reference circles
        canvas.drawCircle(centerX, centerY, maxRadius, circlePaint);
        canvas.drawCircle(centerX, centerY, maxRadius * 0.66f, centerCirclePaint);
        canvas.drawCircle(centerX, centerY, maxRadius * 0.33f, centerCirclePaint);

        // Draw center crosshairs
        circlePaint.setStrokeWidth(1f);
        canvas.drawLine(centerX - 10, centerY, centerX + 10, centerY, circlePaint);
        canvas.drawLine(centerX, centerY - 10, centerX, centerY + 10, circlePaint);
        circlePaint.setStrokeWidth(2f);

        // Calculate ball position based on acceleration (calibrated)
        // Subtract offsets to show relative tilt from calibration point
        float calibratedX = accelX - offsetX;
        float calibratedY = accelY - offsetY;

        // Scale factor: 1 G (9.8 m/s²) should move the ball to the edge
        float maxAccel = 9.8f;  // 1 G
        float ballX = centerX + (calibratedX / maxAccel) * maxRadius * 0.8f;
        float ballY = centerY + (calibratedY / maxAccel) * maxRadius * 0.8f;

        // Clamp ball position to stay within the outer circle
        float distanceFromCenter = (float) Math.sqrt(Math.pow(ballX - centerX, 2) + Math.pow(ballY - centerY, 2));
        if (distanceFromCenter > maxRadius * 0.9f) {
            float angle = (float) Math.atan2(ballY - centerY, ballX - centerX);
            ballX = centerX + (float) Math.cos(angle) * maxRadius * 0.9f;
            ballY = centerY + (float) Math.sin(angle) * maxRadius * 0.9f;
        }

        // Draw the ball with a subtle shadow
        Paint shadowPaint = new Paint(ballPaint);
        shadowPaint.setColor(0x44000000);  // Semi-transparent black
        canvas.drawCircle(ballX + 2, ballY + 2, 12f, shadowPaint);
        canvas.drawCircle(ballX, ballY, 12f, ballPaint);

        // Draw highlight on ball
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(0x88FFFFFF);  // Semi-transparent white
        canvas.drawCircle(ballX - 3, ballY - 3, 4f, highlightPaint);
    }
}
