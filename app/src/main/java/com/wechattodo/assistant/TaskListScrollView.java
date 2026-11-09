package com.wechattodo.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

final class TaskListScrollView extends ScrollView {
    private final int touchSlop;
    private final Runnable onVerticalSwipe;
    private float startX;
    private float startY;
    private boolean tracking;
    private boolean verticalSwipe;

    TaskListScrollView(Context context, Runnable onVerticalSwipe) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.onVerticalSwipe = onVerticalSwipe;
    }

    void revealAfterLayout(View card) {
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                if (!card.isSelected() || !card.isShown() || !card.isAttachedToWindow()) return true;

                Rect bounds = new Rect(0, 0, card.getWidth(), card.getHeight());
                offsetDescendantRectToMyCoords(card, bounds);
                int top = getScrollY() + getPaddingTop();
                int bottom = getScrollY() + getHeight() - getPaddingBottom();
                int delta = bounds.top < top || bounds.height() > bottom - top
                        ? bounds.top - top : Math.max(0, bounds.bottom - bottom);
                if (delta != 0) {
                    if (ValueAnimator.areAnimatorsEnabled()) smoothScrollBy(0, delta);
                    else scrollBy(0, delta);
                }
                return true;
            }
        });
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
                float verticalDistance = Math.abs(event.getY() - startY);
                if (tracking && verticalDistance > touchSlop && verticalDistance > Math.abs(event.getX() - startX)) {
                    tracking = false;
                    verticalSwipe = true;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> tracking = false;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (verticalSwipe) {
            verticalSwipe = false;
            onVerticalSwipe.run();
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Consume the swipe even if the list is too short to scroll, so it cannot become a card tap.
        return super.onInterceptTouchEvent(event) || verticalSwipe;
    }
}
