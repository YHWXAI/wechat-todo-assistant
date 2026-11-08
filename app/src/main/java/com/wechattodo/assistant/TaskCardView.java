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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZoneId;

final class TaskCardView extends LinearLayout {
    enum Mode { STANDARD, COMPACT, EXPANDED }

    private static final int INK = 0xFF19372B;
    private static final int MUTED = 0xFF4A5940;
    private static final int OVERDUE = 0xFF913E28;
    private static final int ACTIVE = 0xFF225138;
    private static final int[] CARD_COLORS = {
            0xFFF4E6D4, 0xFFDDEBE8, 0xFFEAE3F3, 0xFFF3EED3,
            0xFFDDE9F5, 0xFFF3E2E8, 0xFFE5EDD9, 0xFFEAE2DB,
            0xFFDCECF1, 0xFFF3DFDC, 0xFFE2E7F0, 0xFFEFEBE0
    };
    private final TaskStore.Task task;
    private final GradientDrawable surface;
    private final FrameLayout iconTile;
    private final TextView preview;
    private final TextView body;
    private final TextView deadline;
    private final TextView countdown;
    private final Button details;
    private final TextView fullDeadline;
    private final LinearLayout actions;
    private Mode mode;

    TaskCardView(Context context, TaskStore.Task task, TaskCardSource source, Drawable applicationIcon,
                 Mode mode, Runnable showDetails, Runnable complete, Runnable collapse) {
        super(context);
        this.task = task;
        setOrientation(VERTICAL);
        // Use the stored ID so the color stays with the task when its position changes.
        surface = rounded(CARD_COLORS[Math.floorMod(task.id() - 1, CARD_COLORS.length)], 22);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x24225138), surface, null));
        setFocusable(true);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        iconTile = new FrameLayout(context);
        iconTile.setBackground(rounded(0x88FFFFFF, 12));
        iconTile.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (applicationIcon != null || source.app() == null) {
            ImageView icon = new ImageView(context);
            if (applicationIcon != null) {
                icon.setImageDrawable(applicationIcon);
            } else {
                icon.setImageResource(R.drawable.ic_nav_add);
                icon.setImageTintList(ColorStateList.valueOf(ACTIVE));
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER);
            iconParams.setMargins(dp(5), dp(5), dp(5), dp(5));
            iconTile.addView(icon, iconParams);
        } else {
            String abbreviation = source.app().label.substring(0, 1);
            TextView fallback = text(abbreviation, 16, ACTIVE, true);
            fallback.setGravity(Gravity.CENTER);
            iconTile.addView(fallback, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        header.addView(iconTile, new LayoutParams(dp(42), dp(42)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMarginStart(dp(10));
        copyParams.setMarginEnd(dp(8));
        header.addView(copy, copyParams);
        TextView sender = text(source.title(), 16, INK, true);
        sender.setSingleLine(true);
        sender.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(sender);
        preview = text(task.body(), 13, MUTED, false);
        preview.setMaxLines(1);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(preview);

        LinearLayout times = column();
        times.setGravity(Gravity.END);
        header.addView(times, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        deadline = text("", 12, INK, true);
        deadline.setGravity(Gravity.END);
        deadline.setMaxLines(1);
        deadline.setEllipsize(TextUtils.TruncateAt.END);
        deadline.setMaxWidth(dp(128));
        times.addView(deadline);
        countdown = text("", 12, MUTED, false);
        countdown.setGravity(Gravity.END);
        countdown.setMaxLines(1);
        countdown.setEllipsize(TextUtils.TruncateAt.END);
        countdown.setMaxWidth(dp(128));
        addBelow(times, countdown, 2);
        details = action(R.string.task_card_details, showDetails);
        times.addView(details);

        body = text(task.body(), 16, INK, false);
        if (task.done()) {
            preview.setPaintFlags(preview.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            body.setPaintFlags(body.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        addBelow(this, body, 10);
        fullDeadline = text("", 14, MUTED, false);
        addBelow(this, fullDeadline, 12);

        actions = new LinearLayout(context);
        actions.setGravity(Gravity.END);
        actions.addView(action(R.string.task_card_collapse, collapse));
        if (!task.done()) actions.addView(action(R.string.task_card_complete, complete));
        addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setMode(mode);
    }

    void setMode(Mode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        boolean compact = mode == Mode.COMPACT;
        boolean expanded = mode == Mode.EXPANDED;
        int horizontal = dp(expanded ? 18 : 12);
        int vertical = dp(compact ? 6 : expanded ? 16 : 12);
        setPadding(horizontal, vertical, horizontal, vertical);
        setMinimumHeight(dp(compact ? 52 : expanded ? 152 : 96));
        surface.setCornerRadius(dp(compact ? 16 : 22));
        setSelected(expanded);
        var iconParams = iconTile.getLayoutParams();
        iconParams.width = iconParams.height = dp(compact ? 30 : 42);
        iconTile.setLayoutParams(iconParams);
        preview.setVisibility(compact ? VISIBLE : GONE);
        body.setVisibility(compact ? GONE : VISIBLE);
        body.setTextSize(expanded ? 22 : 16);
        body.setMaxLines(expanded ? Integer.MAX_VALUE : 2);
        body.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
        deadline.setVisibility(expanded ? GONE : VISIBLE);
        details.setVisibility(expanded ? VISIBLE : GONE);
        fullDeadline.setVisibility(expanded ? VISIBLE : GONE);
        actions.setVisibility(expanded ? VISIBLE : GONE);
        refreshTime(System.currentTimeMillis());
    }

    long refreshTime(long now) {
        Long due = task.due();
        boolean expanded = mode == Mode.EXPANDED;
        String date = due == null ? "无截止时间" : expanded ? "截止 " + TaskTime.full(due, ZoneId.systemDefault())
                : TaskTime.compact(due, now, ZoneId.systemDefault());
        String remaining = task.done() ? "已完成" : due == null ? "—" : TaskTime.remaining(due, now);
        TextView dateLabel = expanded ? fullDeadline : deadline;
        if (!TextUtils.equals(dateLabel.getText(), date)) dateLabel.setText(date);
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

    private Button action(int label, Runnable listener) {
        Button button = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setTextColor(ACTIVE);
        button.setOnClickListener(view -> listener.run());
        return button;
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
