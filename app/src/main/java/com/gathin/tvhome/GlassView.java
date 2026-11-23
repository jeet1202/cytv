package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated cute chibi black cat focus highlight on a full-screen transparent overlay.
 *
 * - RESTING STATE: The cat hangs by its front paws from the focused card's top rim.
 *   Its back faces the viewer (rear view: fluffy black fur, pointy ears, dangling tail),
 *   while its hind legs actively scratch down against the app logo with claw marks.
 * - JUMPING STATE: When focus moves between apps, a dedicated 450ms animation plays:
 *   1. RUN: Cat pulls up onto the rim and sprints across the card top rim in a gallop stride.
 *   2. LEAP: Launches into a parabolic mid-air ballistic arc (92dp high) with flight tilt & stardust trail.
 *   3. CATCH: Reaches paws forward to catch the destination rim.
 *   4. FALL INTO HANG: Slips over the rim into the rear-view hang and immediately scratches the new logo.
 */
public class GlassView extends View {

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scratchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix drawMatrix = new Matrix();

    // Cat sprite bitmaps
    private Bitmap bmpHang1;  // Rear view hanging steady
    private Bitmap bmpHang2;  // Rear view hanging with hind legs scratching
    private Bitmap bmpCrouch; // Coiled crouch
    private Bitmap bmpRun;    // Extended sprint stride
    private Bitmap bmpLeap;   // Airborne parabolic leap
    private Bitmap bmpCatch;  // Reaching landing catch

    private final float density;
    private int accentColor = 0xFFFFFF;

    // Card coordinates on the screen (in root coordinates)
    private boolean hasTarget = false;
    private float currLeft, currTop, currWidth, currHeight;
    private float targetLeft, targetTop, targetWidth, targetHeight;
    private float prevLeft, prevTop, prevWidth, prevHeight;

    // Jump animation
    private boolean isJumping = false;
    private float jumpProgress = 0f; // 0..1
    private ValueAnimator jumpAnimator;

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
        scratchPaint.setStrokeWidth(1.8f * density);
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

        // Full screen hardware-accelerated overlay
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

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        // Enforce full-screen match_parent so the cat can leap freely across the entire TV screen
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        super.setLayoutParams(params);
    }

    @Override
    public void setTranslationX(float translationX) {
        // Keep the overlay fixed at (0, 0)
        super.setTranslationX(0f);
    }

    @Override
    public void setTranslationY(float translationY) {
        // Keep the overlay fixed at (0, 0)
        super.setTranslationY(0f);
    }

    /** Compatibility with MainActivity: tints stardust / scratch marks. */
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

    /** Called by MainActivity when focus leaves tiles. */
    public void onFocusLost() {
        if (jumpAnimator != null && jumpAnimator.isRunning()) {
            jumpAnimator.cancel();
        }
        isJumping = false;
        sparkles.clear();
        invalidate();
    }

    /** Called by MainActivity whenever the focused card changes. */
    public void onCardTarget(float left, float top, float width, float height, boolean animate) {
        if (!hasTarget) {
            // First time: place directly at card
            hasTarget = true;
            targetLeft = currLeft = prevLeft = left;
            targetTop = currTop = prevTop = top;
            targetWidth = currWidth = prevWidth = width;
            targetHeight = currHeight = prevHeight = height;
            isJumping = false;
            invalidate();
            return;
        }

        // Check if target is same
        if (Math.abs(targetLeft - left) < 2f && Math.abs(targetTop - top) < 2f) {
            return;
        }

        // Save previous card location as jump start
        prevLeft = currLeft;
        prevTop = currTop;
        prevWidth = currWidth;
        prevHeight = currHeight;

        targetLeft = left;
        targetTop = top;
        targetWidth = width;
        targetHeight = height;

        if (!animate) {
            currLeft = targetLeft;
            currTop = targetTop;
            currWidth = targetWidth;
            currHeight = targetHeight;
            isJumping = false;
            invalidate();
            return;
        }

        // Start 450ms run-and-jump animation
        if (jumpAnimator != null && jumpAnimator.isRunning()) {
            jumpAnimator.cancel();
        }

        isJumping = true;
        jumpProgress = 0f;
        sparkles.clear();

        jumpAnimator = ValueAnimator.ofFloat(0f, 1f);
        jumpAnimator.setDuration(450);
        jumpAnimator.setInterpolator(new LinearInterpolator());
        jumpAnimator.addUpdateListener(anim -> {
            jumpProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        jumpAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isJumping = false;
                currLeft = targetLeft;
                currTop = targetTop;
                currWidth = targetWidth;
                currHeight = targetHeight;
                sparkles.clear();
                invalidate();
            }
        });
        jumpAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!hasTarget) return;

        long now = SystemClock.uptimeMillis();

        if (isJumping) {
            // ==================== JUMPING CHOREOGRAPHY ====================
            float t = jumpProgress;

            float srcCenterX = prevLeft + prevWidth / 2f;
            float srcRimY = prevTop;
            float destCenterX = targetLeft + targetWidth / 2f;
            float destRimY = targetTop;

            boolean movingRight = destCenterX >= srcCenterX;
            boolean flip = !movingRight;

            // Takeoff point near the edge of the source card
            float takeoffX = srcCenterX + (movingRight ? 1f : -1f) * (prevWidth * 0.38f);
            // Landing point near destination rim
            float landX = destCenterX - (movingRight ? 1f : -1f) * (targetWidth * 0.15f);

            Bitmap sprite;
            float drawX, drawY;
            float angle = 0f;
            String anchor;

            if (t < 0.25f) {
                // PHASE 1: RIM SPRINT (t: 0..0.25, ~110ms)
                // Runs along source card top rim towards takeoff edge
                float s = t / 0.25f;
                drawX = srcCenterX + (takeoffX - srcCenterX) * (float) Math.pow(s, 1.2);
                drawY = srcRimY;

                // Alternate gallop strides
                boolean stride = ((int) (t * 1000 / 45) % 2 == 0);
                sprite = stride ? bmpRun : bmpCrouch;

                // Sinusoidal gallop bob
                float bob = -4f * density * (float) Math.abs(Math.sin(Math.PI * 2.5 * s));
                drawY += bob;
                angle = flip ? 4f : -4f;
                anchor = "bottom_center";

            } else if (t < 0.78f) {
                // PHASE 2: PARABOLIC MID-AIR LEAP (t: 0.25..0.78, ~240ms)
                float u = (t - 0.25f) / 0.53f; // 0..1 in airborne flight
                drawX = takeoffX + (landX - takeoffX) * u;

                float apexH = 92f * density;
                float interpRimY = srcRimY + (destRimY - srcRimY) * u;
                drawY = interpRimY - 4f * apexH * u * (1f - u);

                // Tangential flight angle: dy/du vs dx
                float dy = (destRimY - srcRimY) - 4f * apexH * (1f - 2f * u);
                float dx = landX - takeoffX;
                float flightAngle = -(float) Math.toDegrees(Math.atan2(dy, dx));
                angle = flip ? -flightAngle : flightAngle;

                sprite = bmpLeap;
                anchor = "center";

                // Emit glowing stardust sparkles behind tail
                if (Math.random() < 0.55) {
                    Sparkle sp = new Sparkle();
                    sp.x = drawX + (flip ? 32f * density : -32f * density);
                    sp.y = drawY + 18f * density;
                    sp.vx = (float) ((Math.random() - 0.5) * 1.5 * density);
                    sp.vy = (float) ((Math.random() - 0.5) * 1.5 * density);
                    sp.size = (float) (2.5f * density + Math.random() * 2f * density);
                    sp.alpha = 240;
                    sparkles.add(sp);
                }

            } else if (t < 0.88f) {
                // PHASE 3: RIM CATCH (t: 0.78..0.88, ~45ms)
                float s = (t - 0.78f) / 0.10f;
                drawX = landX + (destCenterX - landX) * s;
                drawY = destRimY;

                sprite = bmpCatch;
                angle = flip ? 6f : -6f;
                anchor = "bottom_center";

            } else {
                // PHASE 4: SLIP & FALL INTO REAR HANG (t: 0.88..1.0, ~55ms)
                drawX = destCenterX;
                float fallOvershoot = 6f * density * (1f - (t - 0.88f) / 0.12f);
                drawY = destRimY + fallOvershoot;

                sprite = bmpHang1;
                angle = 0f;
                anchor = "rear_hang";
            }

            // Draw current cat pose
            drawSprite(canvas, sprite, drawX, drawY, angle, flip, anchor);

            // Update & draw sparkles
            for (int i = sparkles.size() - 1; i >= 0; i--) {
                Sparkle sp = sparkles.get(i);
                sp.x += sp.vx;
                sp.y += sp.vy;
                sp.alpha -= 16;
                if (sp.alpha <= 0) {
                    sparkles.remove(i);
                } else {
                    sparklePaint.setAlpha(sp.alpha);
                    canvas.drawCircle(sp.x, sp.y, sp.size, sparklePaint);
                }
            }

        } else {
            // ==================== RESTING STATE (REAR VIEW HANG & SCRATCH) ====================
            float centerX = targetLeft + targetWidth / 2f;
            float rimY = targetTop;

            // Scratch cycle: alternate between hang 1 and hang 2 every 130ms
            int frameIdx = (int) ((now / 130) % 4);
            boolean isScratching = (frameIdx == 1 || frameIdx == 2);
            Bitmap sprite = isScratching ? bmpHang2 : bmpHang1;

            // Gentle tail sway
            float pendulumAngle = (float) Math.sin(now * 0.005) * 2.5f;
            float bobY = (float) Math.sin(now * 0.008) * 1.5f * density;

            // Draw rear-view hanging cat (front paws hooked over rim, back seen)
            drawSprite(canvas, sprite, centerX, rimY + bobY, pendulumAngle, false, "rear_hang");

            // Draw claw scratch marks on logo face during scratching
            if (isScratching && bmpHang2 != null) {
                float scratchY = rimY + 54f * density;
                float scratchLen = 12f * density;
                canvas.drawLine(centerX - 8f * density, scratchY, centerX - 11f * density, scratchY + scratchLen, scratchPaint);
                canvas.drawLine(centerX + 9f * density, scratchY, centerX + 6f * density, scratchY + scratchLen, scratchPaint);
            }

            // Continuous smooth 20 FPS resting scratch loop
            postInvalidateDelayed(50);
        }
    }

    private void drawSprite(Canvas canvas, Bitmap bmp, float cx, float topY, float angle, boolean flip, String anchor) {
        if (bmp == null || bmp.isRecycled()) return;

        // Nimble chibi cat scale (~28% of 380px card = ~110px width)
        float targetW = 114f * density;
        float scale = targetW / (float) bmp.getWidth();
        if ("rear_hang".equals(anchor)) {
            // Rear hang sprite has narrower vertical body
            targetW = 62f * density;
            scale = targetW / (float) bmp.getWidth();
        }

        drawMatrix.reset();

        float sw = bmp.getWidth() * scale;
        float sh = bmp.getHeight() * scale;

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

        if (flip) {
            drawMatrix.postScale(-scale, scale, bmp.getWidth() / 2f, bmp.getHeight() / 2f);
        } else {
            drawMatrix.postScale(scale, scale);
        }

        if (Math.abs(angle) > 0.2f) {
            drawMatrix.postRotate(angle, px + sw / 2f, py + sh / 2f);
        }

        drawMatrix.postTranslate(px, py);
        canvas.drawBitmap(bmp, drawMatrix, bmpPaint);
    }
}
