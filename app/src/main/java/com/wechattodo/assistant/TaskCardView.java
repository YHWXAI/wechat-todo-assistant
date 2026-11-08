package com.wechattodo.assistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZoneId;

final class TaskCardView extends LinearLayout {
    private static final int INK = 0xFF19372B;
    private static final int MUTED = 0xFF4A5940;
    private static final int OVERDUE = 0xFF913E28;
    private static final int ACTIVE = 0xFF225138;
    private final TaskStore.Task task;
    private final TextView deadline;
    private final TextView countdown;

    TaskCardView(Context context, TaskStore.Task task, TaskCardSource source, Drawable applicationIcon) {
        super(context);
        this.task = task;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        setMinimumHeight(dp(78));
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x24225138),
                rounded(task.done() ? 0xFFEBF0E1 : 0xFFE9F3CE, 17), null));
        setFocusable(true);

        FrameLayout iconTile = new FrameLayout(context);
        iconTile.setBackground(rounded(task.done() ? 0xFFDDE7CE : 0xFFD4E997, 12));
        iconTile.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (applicationIcon != null || source.app() == null) {
            ImageView icon = new ImageView(context);
            if (applicationIcon != null) {
                icon.setImageDrawable(applicationIcon);
            } else {
                icon.setImageResource(R.drawable.ic_nav_add);
                icon.setImageTintList(ColorStateList.valueOf(ACTIVE));
            }
            iconTile.addView(icon, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        } else {
            String abbreviation = source.app().label.substring(0, 1);
            TextView fallback = text(abbreviation, 16, ACTIVE, true);
            fallback.setGravity(Gravity.CENTER);
            iconTile.addView(fallback, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        addView(iconTile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMarginStart(dp(10));
        copyParams.setMarginEnd(dp(8));
        addView(copy, copyParams);
        TextView sender = text(source.title(), 16, INK, true);
        sender.setSingleLine(true);
        sender.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(sender);
        TextView body = text(task.body(), 14, MUTED, false);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        if (task.done()) body.setPaintFlags(body.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        addBelow(copy, body, 3);

        LinearLayout times = column();
        times.setGravity(Gravity.END);
        addView(times, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        deadline = text("", 12, INK, true);
        deadline.setGravity(Gravity.END);
        deadline.setSingleLine(true);
        times.addView(deadline);
        countdown = text("", 12, MUTED, false);
        countdown.setGravity(Gravity.END);
        countdown.setSingleLine(true);
        addBelow(times, countdown, 4);
        refreshTime(System.currentTimeMillis());
    }

    long refreshTime(long now) {
        Long due = task.due();
        String date = due == null ? "无截止时间" : TaskTime.compact(due, now, ZoneId.systemDefault());
        String remaining = task.done() ? "已完成" : due == null ? "—" : TaskTime.remaining(due, now);
        if (!TextUtils.equals(deadline.getText(), date)) deadline.setText(date);
        if (!TextUtils.equals(countdown.getText(), remaining)) countdown.setText(remaining);
        int color = task.done() || due == null ? MUTED : due <= now ? OVERDUE : ACTIVE;
        if (countdown.getCurrentTextColor() != color) countdown.setTextColor(color);
        return task.done() || due == null ? Long.MAX_VALUE : TaskTime.nextChange(due, now);
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        return column;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addBelow(LinearLayout parent, TextView view, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(gap);
        parent.addView(view, params);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int units) {
        return Math.round(units * getResources().getDisplayMetrics().density);
    }
}
