package com.wechattodo.assistant;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

final class TaskListScrollView extends ScrollView {
    private final int touchSlop;
    private final Runnable onSwipeUp;
    private float startX;
    private float startY;
    private boolean tracking;
    private boolean swipeUp;

    TaskListScrollView(Context context, Runnable onSwipeUp) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.onSwipeUp = onSwipeUp;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                startX = event.getX();
                startY = event.getY();
                tracking = true;
            }
            case MotionEvent.ACTION_MOVE -> {
                float rise = startY - event.getY();
                if (tracking && rise > touchSlop && rise > Math.abs(event.getX() - startX)) {
                    tracking = false;
                    swipeUp = true;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> tracking = false;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (swipeUp) {
            swipeUp = false;
            onSwipeUp.run();
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Consume the swipe even if the list is too short to scroll, so it cannot become a card tap.
        return super.onInterceptTouchEvent(event) || swipeUp;
    }
}
