package com.aether.launcher.hud;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class GyroParallaxManager implements SensorEventListener {

    public interface ParallaxListener {
        void onParallax(float pitchOffset, float rollOffset);
    }

    private final SensorManager sensorManager;
    private Sensor rotationSensor;
    private ParallaxListener listener;

    private boolean enabled = true;
    private float targetPitch = 0f;
    private float targetRoll = 0f;
    private float currentPitch = 0f;
    private float currentRoll = 0f;

    // Smoothing factor (0.1 = smooth & buttery, 0.5 = reactive)
    private static final float SMOOTH_FACTOR = 0.15f;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationVals = new float[3];

    public GyroParallaxManager(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor == null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }
    }

    public void start(ParallaxListener listener) {
        this.listener = listener;
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        this.listener = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            targetPitch = 0f;
            targetRoll = 0f;
            currentPitch = 0f;
            currentRoll = 0f;
            if (listener != null) listener.onParallax(0f, 0f);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!enabled || listener == null) return;

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationVals);

            // pitch (around X-axis) and roll (around Y-axis) in radians
            float pitchRad = orientationVals[1];
            float rollRad = orientationVals[2];

            // Normalize between -1.0 and 1.0 (clamped to approx 45 degrees tilt)
            targetPitch = Math.max(-1.0f, Math.min(1.0f, pitchRad / 0.8f));
            targetRoll = Math.max(-1.0f, Math.min(1.0f, -rollRad / 0.8f));
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Fallback for accelerometer: X is roll, Y is pitch
            targetRoll = Math.max(-1.0f, Math.min(1.0f, -event.values[0] / 8.0f));
            targetPitch = Math.max(-1.0f, Math.min(1.0f, (event.values[1] - 5.0f) / 8.0f));
        }

        // Apply low-pass exponential smoothing
        currentPitch += (targetPitch - currentPitch) * SMOOTH_FACTOR;
        currentRoll += (targetRoll - currentRoll) * SMOOTH_FACTOR;

        listener.onParallax(currentPitch, currentRoll);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
