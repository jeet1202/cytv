package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.Random;

/**
 * Focus effect rendering a lively swarm of 58 luminous butterflies and golden bees.
 * When resting, the swarm orbits and flutters behind the focused app card, peeking out
 * around its edges. When jumping between apps, the swarm breaks formation and streams
 * through the open air behind the cards to settle behind the new destination app.
 */
public class GlassView extends View {

    private static final int NUM_CREATURES = 58;

    private static final class Creature {
        boolean isBee;
        int wingColor;
        int glowColor;
        int bodyColor;
        float size;
        float flapSpeed;
        float phase;
        float angle;
        float orbitSpeed;
        float orbitRx;
        float orbitRy;
        float flutterAmp;
        float flutterFreq;
        float arc;
        float followSpeed;

        float x, y;
        float prevX, prevY;

        static final int MAX_TRAIL = 5;
        float[] trailX = new float[MAX_TRAIL];
        float[] trailY = new float[MAX_TRAIL];
        long[] trailTime = new long[MAX_TRAIL];
        int trailCount = 0;
    }

    private final Creature[] creatures = new Creature[NUM_CREATURES];
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float density;
    private float radius;
    private float focusX = -1f;
    private float focusY = -1f;
    private float cardWidth = 0f;
    private float cardHeight = 0f;
    private long lastFrameTime = 0;
    private boolean initialized = false;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;

        glowPaint.setStyle(Paint.Style.FILL);
        wingPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(0xE6FFFFFF);
        bodyPaint.setStyle(Paint.Style.FILL);
        stripePaint.setStyle(Paint.Style.STROKE);
        stripePaint.setStrokeWidth(1.2f * density);
        stripePaint.setColor(0xCC2A1B00);
        trailPaint.setStyle(Paint.Style.FILL);

        initCreatures();
    }

    private void initCreatures() {
        Random rnd = new Random(4242);
        int[] paletteWings = {
                0xD000E5FF, // Morpho Azure
                0xD0E040FB, // Sunset Orchid
                0xD069F0AE, // Emerald Jade
                0xD0FFAB40, // Monarch Amber
                0xD080D8FF, // Electric Ice
                0xD0FF80AB, // Rose Quartz
                0xD0FFD700  // Golden Bee
        };
        int[] paletteGlow = {
                0x0091EA, 0xAA00FF, 0x00C853, 0xFF6D00, 0x00B0FF, 0xF50057, 0xFF8F00
        };

        for (int i = 0; i < NUM_CREATURES; i++) {
            Creature c = new Creature();
            c.isBee = (i % 6 == 0);
            int pIdx = c.isBee ? 6 : (i % 6);
            c.wingColor = paletteWings[pIdx];
            c.glowColor = paletteGlow[pIdx];
            c.bodyColor = c.isBee ? 0xFFFFEB3B : 0xFFFFFFFF;
            c.size = (c.isBee ? (7.5f + rnd.nextFloat() * 3.5f) : (10.5f + rnd.nextFloat() * 5.5f)) * density;
            c.flapSpeed = c.isBee ? (30f + rnd.nextFloat() * 14f) : (15f + rnd.nextFloat() * 10f);
            c.phase = rnd.nextFloat() * (float) (Math.PI * 2.0);
            c.angle = ((float) i / NUM_CREATURES) * (float) (Math.PI * 2.0) + (rnd.nextFloat() - 0.5f) * 0.35f;
            c.orbitSpeed = (0.55f + rnd.nextFloat() * 0.75f) * (rnd.nextBoolean() ? 1f : -1f);
            c.orbitRx = (rnd.nextFloat() - 0.5f) * 32f * density;
            c.orbitRy = (rnd.nextFloat() - 0.5f) * 26f * density;
            c.flutterAmp = (3f + rnd.nextFloat() * 5f) * density;
            c.flutterFreq = 3f + rnd.nextFloat() * 3.5f;
            c.arc = (35f + rnd.nextFloat() * 55f) * density * (rnd.nextBoolean() ? 1f : -1f);
            c.followSpeed = 10f + rnd.nextFloat() * 8f;
            creatures[i] = c;
        }
    }

    void setAccent(int argb) {
        // Preserves signature for MainActivity
    }

    void setStyle(String style) {
        // Preserves signature for MainActivity
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if (params != null) {
            if (params.width > 0 && params.height > 0) {
                cardWidth = params.width;
                cardHeight = params.height;
            }
            // Retain full screen dimensions so particles can fly anywhere across the display
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        super.setLayoutParams(params);
    }

    @Override
    public void setTranslationX(float translationX) {
        this.focusX = translationX;
        super.setTranslationX(0f);
        invalidate();
    }

    @Override
    public float getTranslationX() {
        return focusX >= 0f ? focusX : 0f;
    }

    @Override
    public void setTranslationY(float translationY) {
        this.focusY = translationY;
        super.setTranslationY(0f);
        invalidate();
    }

    @Override
    public float getTranslationY() {
        return focusY >= 0f ? focusY : 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrameTime > 0 ? (now - lastFrameTime) / 1000f : 0.016f;
        dt = Math.max(0.001f, Math.min(0.05f, dt));
        lastFrameTime = now;
        float time = now / 1000f;

        float cw = cardWidth > 0 ? cardWidth : (180f * density);
        float ch = cardHeight > 0 ? cardHeight : (105f * density);
        float targetCenterX = (focusX >= 0f ? focusX : (getWidth() / 2f - cw / 2f)) + cw / 2f;
        float targetCenterY = (focusY >= 0f ? focusY : (getHeight() / 2f - ch / 2f)) + ch / 2f;

        float halfW = cw / 2f;
        float halfH = ch / 2f;

        for (int i = 0; i < NUM_CREATURES; i++) {
            Creature c = creatures[i];

            float a = c.angle + c.orbitSpeed * time;
            float flX = (float) Math.sin(time * c.flutterFreq + c.phase) * c.flutterAmp;
            float flY = (float) Math.cos(time * c.flutterFreq * 1.3f + c.phase) * c.flutterAmp;

            float rx = halfW + c.orbitRx;
            float ry = halfH + c.orbitRy;
            float targetX = targetCenterX + (float) Math.cos(a) * rx + flX;
            float targetY = targetCenterY + (float) Math.sin(a) * ry + flY;

            if (!initialized) {
                c.x = targetX;
                c.y = targetY;
            }

            float dx = targetX - c.x;
            float dy = targetY - c.y;
            float dist = (float) Math.hypot(dx, dy);

            c.prevX = c.x;
            c.prevY = c.y;
            c.x += dx * Math.min(1f, c.followSpeed * dt);
            c.y += dy * Math.min(1f, c.followSpeed * dt);

            float flightLift = (dist > 15f * density)
                    ? (float) Math.sin(Math.min(Math.PI, dist / (180f * density) * Math.PI)) * c.arc
                    : 0f;
            float drawX = c.x;
            float drawY = c.y - flightLift;

            float heading = (dist > 12f * density)
                    ? (float) Math.atan2(dy - flightLift, dx)
                    : a + (float) (Math.PI / 2.0);

            // Sparkle trails during mid-flight
            if (dist > 30f * density) {
                if (c.trailCount < Creature.MAX_TRAIL) {
                    c.trailX[c.trailCount] = drawX;
                    c.trailY[c.trailCount] = drawY;
                    c.trailTime[c.trailCount] = now;
                    c.trailCount++;
                } else {
                    int idx = (int) ((now / 40) % Creature.MAX_TRAIL);
                    c.trailX[idx] = drawX;
                    c.trailY[idx] = drawY;
                    c.trailTime[idx] = now;
                }
            }

            // Draw sparkle trails
            for (int tIdx = 0; tIdx < c.trailCount; tIdx++) {
                long ageMs = now - c.trailTime[tIdx];
                if (ageMs < 250) {
                    float life = 1f - (float) ageMs / 250f;
                    trailPaint.setColor(0x00FFFFFF | ((int) (life * 140) << 24));
                    canvas.drawCircle(c.trailX[tIdx], c.trailY[tIdx], Math.max(1f, life * 2.5f * density), trailPaint);
                }
            }

            // 1. Luminous outer glow
            float gr = c.size * 1.6f;
            glowPaint.setColor((0x2A << 24) | c.glowColor);
            canvas.drawCircle(drawX, drawY, gr, glowPaint);
            glowPaint.setColor((0x60 << 24) | c.glowColor);
            canvas.drawCircle(drawX, drawY, c.size * 0.8f, glowPaint);

            // 2. Wings (with 3D perspective foreshortening)
            float flap = (float) Math.sin(time * c.flapSpeed + c.phase);
            float wSpan = Math.abs(flap) * c.size * 0.85f + 2f * density;
            float wLen = c.size * 0.65f;
            float cosH = (float) Math.cos(heading);
            float sinH = (float) Math.sin(heading);

            wingPaint.setColor(c.wingColor);

            for (int side = -1; side <= 1; side += 2) {
                float wx = drawX + side * wSpan * 0.55f * cosH;
                float wy = drawY + side * wSpan * 0.55f * sinH;
                oval.set(wx - wSpan * 0.5f, wy - wLen * 0.5f, wx + wSpan * 0.5f, wy + wLen * 0.5f);
                canvas.drawOval(oval, wingPaint);

                oval.set(wx - wSpan * 0.25f, wy - wLen * 0.25f, wx + wSpan * 0.25f, wy + wLen * 0.25f);
                canvas.drawOval(oval, highlightPaint);
            }

            // 3. Body & stripes
            float br = c.size * 0.25f;
            bodyPaint.setColor(c.bodyColor);
            canvas.drawCircle(drawX, drawY, br, bodyPaint);

            if (c.isBee) {
                canvas.drawLine(drawX - br * 0.7f, drawY, drawX + br * 0.7f, drawY, stripePaint);
            }
        }

        initialized = true;

        if (getVisibility() == VISIBLE && getAlpha() > 0f) {
            postInvalidateOnAnimation();
        }
    }
}
