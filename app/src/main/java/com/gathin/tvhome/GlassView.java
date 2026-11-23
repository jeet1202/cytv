package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated cute chibi black cat focus highlight replacing the old glass slab.
 *
 * - RESTING STATE: Cat hangs from the top rim of the focused card, seen from BEHIND (rear view).
 *   Its front paws hook over the top edge of the card, its glossy black back and ears face the viewer,
 *   its long tail dangles down, and its hind legs actively scratch down against the app logo.
 * - JUMPING STATE: When focus moves between apps, the cat pulls up onto the rim, springs into a
 *   realistic parabolic leap with dynamic flight tilt and stardust sparkle particles, catches the
 *   destination rim, and slips smoothly down into the rear-view hanging pose on the new app.
 */
public class GlassView extends View {

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scratchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix drawMatrix = new Matrix();

    // Cat sprite bitmaps
    private Bitmap bmpHang1;  // Rear view hanging steady
    private Bitmap bmpHang2;  // Rear view hanging with hind legs scratching
    private Bitmap bmpCrouch; // Coiled crouch / ready
    private Bitmap bmpRun;    // Extended sprint stride
    private Bitmap bmpLeap;   // Airborne parabolic leap
    private Bitmap bmpCatch;  // Reaching landing catch

    private final float density;
    private int accentColor = 0xFFFFFF;

    // Motion tracking for spring transitions
    private float prevTransX = Float.NaN;
    private float prevTransY = Float.NaN;
    private float smoothVelX = 0f;
    private float smoothVelY = 0f;
    private boolean isMoving = false;
    private long moveStartTime = 0;
    private long lastAnimTime = 0;
    private boolean movingRight = true;

    // Jump arc physics
    private float jumpProgress = 0f; // 0..1 during transition
    private float totalJumpDist = 0f;
    private float jumpStartX = 0f;

    // Stardust sparkles
    private static class Sparkle {
        float x, y;
        float vx, vy;
        float size;
        int alpha;
    }
    private final List<Sparkle> sparkles = new ArrayList<>();

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        scratchPaint.setStyle(Paint.Style.STROKE);
        scratchPaint.setStrokeWidth(1.5f * density);
        scratchPaint.setColor(0xB3FFFFFF);
        scratchPaint.setStrokeCap(Paint.Cap.ROUND);

        sparklePaint.setStyle(Paint.Style.FILL);
        sparklePaint.setColor(0xFFFFE68C);

        // Load cat bitmaps
        try {
            bmpHang1 = BitmapFactory.decodeResource(getResources(), R.drawable.cat_rear_hang_1);
            bmpHang2 = BitmapFactory.decodeResource(getResources(), R.drawable.cat_rear_hang_2);
            bmpCrouch = BitmapFactory.decodeResource(getResources(), R.drawable.cat_crouch);
            bmpRun = BitmapFactory.decodeResource(getResources(), R.drawable.cat_run);
            bmpLeap = BitmapFactory.decodeResource(getResources(), R.drawable.cat_leap);
            bmpCatch = BitmapFactory.decodeResource(getResources(), R.drawable.cat_catch);
        } catch (Throwable ignored) {}

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).setClipChildren(false);
            ((ViewGroup) getParent()).setClipToPadding(false);
        }
    }

    /** Compatibility with MainActivity: tints the stardust sparkles / scratch accent. */
    void setAccent(int argb) {
        if (argb != 0) {
            accentColor = argb;
            scratchPaint.setColor(0xCC000000 | (argb & 0xFFFFFF));
            sparklePaint.setColor(0xEE000000 | (argb & 0xFFFFFF));
        } else {
            accentColor = 0xFFFFFF;
            scratchPaint.setColor(0xB3FFFFFF);
            sparklePaint.setColor(0xFFFFE68C);
        }
        invalidate();
    }

    /** Compatibility with MainActivity. */
    void setStyle(String style) {
        invalidate();
    }

    /** Compatibility with MainActivity. */
    void setRadius(float px) {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        float curTransX = getTranslationX();
        float curTransY = getTranslationY();

        // 1. Detect movement from SpringAnimation
        if (!Float.isNaN(prevTransX) && !Float.isNaN(prevTransY)) {
            float dx = curTransX - prevTransX;
            float dy = curTransY - prevTransY;
            smoothVelX = smoothVelX * 0.6f + dx * 0.4f;
            smoothVelY = smoothVelY * 0.6f + dy * 0.4f;

            float speed = (float) Math.hypot(smoothVelX, smoothVelY);
            if (speed > 1.2f * density) {
                if (!isMoving) {
                    isMoving = true;
                    moveStartTime = now;
                    movingRight = smoothVelX >= 0;
                    jumpStartX = prevTransX;
                    totalJumpDist = Math.max(100f * density, Math.abs(smoothVelX) * 15f);
                }
                if (Math.abs(smoothVelX) > 0.5f) {
                    movingRight = smoothVelX >= 0;
                }
            } else {
                if (isMoving && (now - moveStartTime > 250)) {
                    isMoving = false;
                }
            }
        }
        prevTransX = curTransX;
        prevTransY = curTransY;
        lastAnimTime = now;

        // Top rim of card is inset by 6dp pad inside this view
        float pad = 6f * density;
        float rimY = pad;
        float cardCenterX = w / 2f;

        if (isMoving) {
            // ==================== JUMPING STATE ====================
            // Calculate jump progress u based on elapsed move time (360ms complete jump)
            long elapsed = now - moveStartTime;
            float u = Math.min(1.0f, elapsed / 360f);

            Bitmap sprite;
            float spriteAngle = 0f;
            float yOffset = 0f;
            boolean flip = !movingRight;

            if (u < 0.18f) {
                // Phase 1: Pull up onto rim & gallop sprint
                sprite = (u < 0.09f) ? bmpCrouch : bmpRun;
                yOffset = -rimY; // On top of rim
                spriteAngle = flip ? 3f : -3f;
            } else if (u < 0.80f) {
                // Phase 2: Parabolic mid-air flight
                sprite = bmpLeap;
                float flightU = (u - 0.18f) / 0.62f; // 0..1 in flight
                float apexH = 88f * density;
                yOffset = -rimY - 4f * apexH * flightU * (1f - flightU);

                // Dynamic flight tilt tangent: dy/du
                float dy = -4f * apexH * (1f - 2f * flightU);
                float dx = totalJumpDist * (movingRight ? 1f : -1f);
                float angle = -(float) Math.toDegrees(Math.atan2(dy, dx));
                spriteAngle = flip ? -angle : angle;

                // Emit stardust sparkles behind tail
                if (Math.random() < 0.45) {
                    Sparkle s = new Sparkle();
                    s.x = cardCenterX + (flip ? 30f * density : -30f * density);
                    s.y = rimY + yOffset + 20f * density;
                    s.vx = (float) ((Math.random() - 0.5) * 1.5 * density);
                    s.vy = (float) ((Math.random() - 0.5) * 1.5 * density);
                    s.size = (float) (2f * density + Math.random() * 2f * density);
                    s.alpha = 220;
                    sparkles.add(s);
                }
            } else if (u < 0.92f) {
                // Phase 3: Reach landing rim
                sprite = bmpCatch;
                yOffset = -rimY * 0.5f;
                spriteAngle = flip ? 6f : -6f;
            } else {
                // Phase 4: Touchdown, slip & fall into rear-view hang
                sprite = bmpHang1;
                yOffset = 0f;
                spriteAngle = 0f;
            }

            drawSprite(canvas, sprite, cardCenterX, rimY + yOffset, spriteAngle, flip, "center");

            // Update & draw sparkles
            for (int i = sparkles.size() - 1; i >= 0; i--) {
                Sparkle s = sparkles.get(i);
                s.x += s.vx;
                s.y += s.vy;
                s.alpha -= 15;
                if (s.alpha <= 0) {
                    sparkles.remove(i);
                } else {
                    sparklePaint.setAlpha(s.alpha);
                    canvas.drawCircle(s.x, s.y, s.size, sparklePaint);
                }
            }

            postInvalidateOnAnimation();
        } else {
            // ==================== RESTING STATE (REAR VIEW HANG & SCRATCH) ====================
            // Scratch cycle: alternate between hang 1 and hang 2 every 140ms
            int frameIdx = (int) ((now / 140) % 4);
            boolean isScratching = (frameIdx == 1 || frameIdx == 2);
            Bitmap sprite = isScratching ? bmpHang2 : bmpHang1;

            // Subtle gentle tail pendulum sway (sin wave ~ 2.5 degrees)
            float pendulumAngle = (float) Math.sin(now * 0.005) * 2.5f;
            float bobY = (float) Math.sin(now * 0.008) * 1.5f * density;

            // Draw rear-view hanging cat (front paws hooked over card rim)
            drawSprite(canvas, sprite, cardCenterX, rimY + bobY, pendulumAngle, false, "rear_hang");

            // Draw 2 delicate claw scratch marks on the logo face during scratching
            if (isScratching && bmpHang2 != null) {
                float scratchY = rimY + 52f * density;
                float scratchLen = 10f * density;
                // Left paw scratch
                canvas.drawLine(cardCenterX - 7f * density, scratchY, cardCenterX - 10f * density, scratchY + scratchLen, scratchPaint);
                // Right paw scratch
                canvas.drawLine(cardCenterX + 8f * density, scratchY, cardCenterX + 5f * density, scratchY + scratchLen, scratchPaint);
            }

            // Clean up any remaining sparkles
            sparkles.clear();

            // Next frame for smooth resting scratch animation (20 FPS loop)
            postInvalidateDelayed(50);
        }
    }

    private void drawSprite(Canvas canvas, Bitmap bmp, float cx, float topY, float angle, boolean flip, String anchor) {
        if (bmp == null || bmp.isRecycled()) return;

        // Base scale: cat is nimble chibi scale (~28% of typical 380px card width = ~110px width)
        float targetW = 112f * density;
        float scale = targetW / (float) bmp.getWidth();
        if ("rear_hang".equals(anchor)) {
            // Rear hang sprite has narrower width and vertical body
            targetW = 62f * density;
            scale = targetW / (float) bmp.getWidth();
        }

        drawMatrix.reset();

        float sw = bmp.getWidth() * scale;
        float sh = bmp.getHeight() * scale;

        // Anchor positioning
        float px, py;
        if ("rear_hang".equals(anchor)) {
            // Front paws hook over top rim by ~8dp
            px = cx - sw / 2f;
            py = topY - 8f * density;
        } else if ("center".equals(anchor)) {
            px = cx - sw / 2f;
            py = topY - sh / 2f;
        } else {
            // bottom_center
            px = cx - sw / 2f;
            py = topY - sh;
        }

        // Apply flip
        if (flip) {
            drawMatrix.postScale(-scale, scale, bmp.getWidth() / 2f, bmp.getHeight() / 2f);
        } else {
            drawMatrix.postScale(scale, scale);
        }

        // Apply rotation
        if (Math.abs(angle) > 0.2f) {
            drawMatrix.postRotate(angle, px + sw / 2f, py + sh / 2f);
        }

        drawMatrix.postTranslate(px, py);
        canvas.drawBitmap(bmp, drawMatrix, bmpPaint);
    }
}
