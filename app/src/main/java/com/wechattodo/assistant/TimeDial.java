package com.wechattodo.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;


final class TimeDial extends View {
    private static final int INK = 0xFF17332B;
    private static final int MINUTE_ACCENT = 0xFF116654;
    private static final int HOUR_ACCENT = 0xFF3C648B;
    private static final double FULL_TURN = 2 * Math.PI;
    private static final double POINTER_ANGLE = -Math.PI / 2;
    private static final float RING_DIVIDER = 0.76f;
    private static final float FACE_RADIUS = 0.50f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int hour;
    private int minute;
    private final int touchSlop;
    private int activeSteps;
    private int startValue;
    private float downX;
    private float downY;
    private double lastAngle;
    private double rotation;
    private boolean dragging;
    private String displayTime;

    TimeDial(Context context, int hour, int minute) {
        super(context);
        this.hour = hour;
        this.minute = minute;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        updateTime();
    }

    int hour() { return hour; }

    int minute() { return minute; }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(Math.round(dp(270)), widthSpec);
        int height = Math.min(Math.round(width * 1.62f), Math.round(dp(400)));
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x = centerX();
        float y = getHeight() / 2f;
        float radius = radius();
        disk(canvas, x, y, radius, 0xFFF8FBFC);
        disk(canvas, x, y, radius * RING_DIVIDER, 0xFFDCE5EC);
        disk(canvas, x, y, radius * FACE_RADIUS, 0xFFF8FBFC);
        drawRing(canvas, x, y, radius, 60, minute, MINUTE_ACCENT, 0.94f, 0.83f);
        drawRing(canvas, x, y, radius, 24, hour, HOUR_ACCENT, 0.72f, 0.59f);

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float faceLeft = x - radius * FACE_RADIUS;
        float visibleFaceWidth = getWidth() - faceLeft;
        paint.setTextSize(Math.min(sp(32), visibleFaceWidth * 0.32f));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(INK);
        canvas.drawText(displayTime, faceLeft + visibleFaceWidth / 2f,
                y - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void disk(Canvas canvas, float x, float y, float radius, int color) {
        paint.setColor(color);
        paint.setShadowLayer(dp(4), 0, dp(2), 0x46314F61);
        canvas.drawCircle(x, y, radius, paint);
        paint.clearShadowLayer();
    }

    private void drawRing(Canvas canvas, float x, float y, float radius, int steps, int selected,
                          int accent, float tickRadius, float labelRadius) {
        int major = steps == 60 ? 5 : 3;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.min(sp(10), radius * 0.10f));
        for (int step = 0; step < steps; step++) {
            double angle = POINTER_ANGLE + (step - selected) * FULL_TURN / steps;
            float dx = (float) Math.sin(angle);
            float dy = -(float) Math.cos(angle);
            boolean labeled = step % major == 0;
            float end = radius * tickRadius;
            float start = end - dp(step == selected ? 8 : labeled ? 6 : 3);
            paint.setColor(step == selected ? accent : labeled ? 0xFF6C828C : 0xFF9CAEB5);
            paint.setStrokeWidth(dp(step == selected ? 4 : labeled ? 2 : 1));
            canvas.drawLine(x + dx * start, y + dy * start, x + dx * end, y + dy * end, paint);
            if (labeled) {
                float baseline = y + dy * radius * labelRadius - (paint.ascent() + paint.descent()) / 2f;
                canvas.drawText(twoDigits(step), x + dx * radius * labelRadius, baseline, paint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float dx = event.getX() - centerX();
        float dy = event.getY() - getHeight() / 2f;
        double distance = Math.hypot(dx, dy);
        double angle = Math.atan2(dx, -dy);
        float radius = radius();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (distance < radius * FACE_RADIUS || distance > radius + dp(5)) return false;
                activeSteps = distance >= radius * RING_DIVIDER ? 60 : 24;
                startValue = activeSteps == 60 ? minute : hour;
                downX = dx;
                downY = dy;
                lastAngle = angle;
                rotation = 0;
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeSteps == 0) return false;
                if (distance > radius / 4f) rotate(dx, dy, angle);
                return true;
            case MotionEvent.ACTION_UP:
                if (activeSteps == 0) return false;
                if (distance > radius / 4f) {
                    rotate(dx, dy, angle);
                    if (!dragging) {
                        select(startValue + Math.round((angle - POINTER_ANGLE) * activeSteps / FULL_TURN));
                        performClick();
                    }
                }
                activeSteps = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                return true;
            case MotionEvent.ACTION_CANCEL:
                activeSteps = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return activeSteps != 0;
        }
    }

    private void rotate(float dx, float dy, double angle) {
        double delta = angle - lastAngle;
        if (delta > Math.PI) delta -= FULL_TURN;
        else if (delta < -Math.PI) delta += FULL_TURN;
        rotation += delta;
        lastAngle = angle;
        if (!dragging) dragging = Math.hypot(dx - downX, dy - downY) > touchSlop;
        if (dragging) select(startValue - Math.round(rotation * activeSteps / FULL_TURN));
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.hour_up, getContext().getText(R.string.deadline_hour_up)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.hour_down, getContext().getText(R.string.deadline_hour_down)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.minute_up, getContext().getText(R.string.deadline_minute_up)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.minute_down, getContext().getText(R.string.deadline_minute_down)));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.id.hour_up) hour = (hour + 1) % 24;
        else if (action == R.id.hour_down) hour = (hour + 23) % 24;
        else if (action == R.id.minute_up) minute = (minute + 1) % 60;
        else if (action == R.id.minute_down) minute = (minute + 59) % 60;
        else return super.performAccessibilityAction(action, arguments);
        updateTime();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        return true;
    }

    private void select(long step) {
        int value = Math.floorMod(step, activeSteps);
        if (activeSteps == 60 && minute != value) {
            minute = value;
            updateTime();
        } else if (activeSteps == 24 && hour != value) {
            hour = value;
            updateTime();
        }
    }

    private void updateTime() {
        displayTime = twoDigits(hour) + ":" + twoDigits(minute);
        setContentDescription(getContext().getString(R.string.deadline_dial_value, displayTime));
        invalidate();
    }

    private float centerX() {
        return getWidth() - radius() * 0.12f;
    }

    private float radius() {
        return Math.min(getWidth() * 0.82f, getHeight() * 0.47f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
