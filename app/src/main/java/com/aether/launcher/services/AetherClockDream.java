package com.aether.launcher.services;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.aether.launcher.hud.HologramTheme;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class AetherClockDream extends DreamService {

    private ValueAnimator dreamLoop;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        View dreamView = new View(this) {
            private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            private final Random random = new Random();

            private float[] dropX;
            private float[] dropY;
            private float[] dropSpeed;
            private int columns = 0;

            {
                rainPaint.setColor(HologramTheme.get().getPrimary());
                rainPaint.setTextSize(20f);
                rainPaint.setTypeface(android.graphics.Typeface.MONOSPACE);

                clockPaint.setColor(0xFFF0F8FF);
                clockPaint.setTextAlign(Paint.Align.CENTER);
                clockPaint.setFakeBoldText(true);

                subPaint.setColor(HologramTheme.get().getPrimary());
                subPaint.setTextAlign(Paint.Align.CENTER);
                subPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                columns = Math.max(1, w / 28);
                dropX = new float[columns];
                dropY = new float[columns];
                dropSpeed = new float[columns];

                for (int i = 0; i < columns; i++) {
                    dropX[i] = i * 28f + 14f;
                    dropY[i] = random.nextFloat() * h;
                    dropSpeed[i] = 4f + random.nextFloat() * 10f;
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;

                // Dark background
                canvas.drawColor(0xFF04070D);

                // Draw Digital Matrix Rain
                if (dropY != null) {
                    for (int i = 0; i < columns; i++) {
                        dropY[i] += dropSpeed[i];
                        if (dropY[i] > h) {
                            dropY[i] = 0;
                            dropSpeed[i] = 4f + random.nextFloat() * 10f;
                        }

                        // Draw glyph stream
                        for (int j = 0; j < 6; j++) {
                            float gy = dropY[i] - (j * 22f);
                            if (gy > 0 && gy < h) {
                                int alpha = Math.max(20, 255 - (j * 40));
                                rainPaint.setColor((HologramTheme.get().getPrimary() & 0x00FFFFFF) | (alpha << 24));
                                char glyph = (char) ('A' + (random.nextInt(26)));
                                canvas.drawText(String.valueOf(glyph), dropX[i], gy, rainPaint);
                            }
                        }
                    }
                }

                // Center Sci-Fi Clock
                clockPaint.setTextSize(Math.min(w, h) * 0.16f);
                float cx = w / 2f;
                float cy = h / 2f;
                canvas.drawText(timeFormat.format(new Date()), cx, cy, clockPaint);

                subPaint.setTextSize(Math.min(w, h) * 0.035f);
                canvas.drawText("AETHER // DORMANT RE-CALIBRATION", cx, cy + (clockPaint.getTextSize() * 0.5f), subPaint);
            }
        };

        setContentView(dreamView);

        dreamLoop = ValueAnimator.ofFloat(0f, 1f);
        dreamLoop.setDuration(1000);
        dreamLoop.setRepeatCount(ValueAnimator.INFINITE);
        dreamLoop.setInterpolator(new LinearInterpolator());
        dreamLoop.addUpdateListener(anim -> dreamView.invalidate());
        dreamLoop.start();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (dreamLoop != null) dreamLoop.cancel();
    }
}
