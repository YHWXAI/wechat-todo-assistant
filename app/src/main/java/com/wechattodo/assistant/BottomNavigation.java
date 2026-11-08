package com.wechattodo.assistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.function.Consumer;

final class BottomNavigation extends FrameLayout {
    static final int BAR_COLOR = 0xFF292B2E;
    private static final int BAR_OFFSET = 18;
    private static final int ROW_HEIGHT = 60;
    private static final int ACTIVE = 0xFFE2F4A7;
    private static final int INACTIVE = 0xFFE0EBE5;

    enum Tab {
        HOME(R.string.nav_home, R.drawable.ic_nav_home), DAILY(R.string.nav_daily, R.drawable.ic_nav_daily),
        WORK(R.string.nav_work, R.drawable.ic_nav_work), SETTINGS(R.string.settings_title, R.drawable.ic_nav_settings);

        final int label;
        final int icon;

        Tab(int label, int icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private record TabItem(LinearLayout target, ImageView icon, TextView caption) {}

    private final EnumMap<Tab, TabItem> tabs = new EnumMap<>(Tab.class);

    BottomNavigation(Context context, int surfaceColor, Consumer<Tab> onTab, Runnable onAdd) {
        super(context);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(BAR_COLOR);
        float corner = dp(18);
        surface.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        View bar = new View(context);
        bar.setBackground(surface);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        barParams.topMargin = dp(BAR_OFFSET);
        addView(bar, barParams);

        LinearLayout row = new LinearLayout(context);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        addTab(row, Tab.HOME, onTab);
        addTab(row, Tab.DAILY, onTab);
        row.addView(new View(context), slot());
        addTab(row, Tab.WORK, onTab);
        addTab(row, Tab.SETTINGS, onTab);
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT));
        rowParams.topMargin = dp(BAR_OFFSET);
        addView(row, rowParams);

        ImageButton add = new ImageButton(context);
        add.setContentDescription(context.getText(R.string.manual_add));
        add.setImageResource(R.drawable.ic_nav_add);
        add.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setPadding(0, 0, 0, 0);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF3B3E43);
        circle.setStroke(dp(3), surfaceColor);
        add.setBackground(new RippleDrawable(ColorStateList.valueOf(0x38FFFFFF), circle, null));
        add.setElevation(dp(3));
        add.setOnClickListener(view -> onAdd.run());
        int diameter = dp(58);
        addView(add, new FrameLayout.LayoutParams(diameter, diameter, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        select(Tab.HOME);
    }

    int heightWithInset(int bottomInset) {
        return dp(BAR_OFFSET + ROW_HEIGHT) + bottomInset;
    }

    void select(Tab selected) {
        tabs.forEach((tab, item) -> {
            boolean active = tab == selected;
            int tint = active ? ACTIVE : INACTIVE;
            item.target().setSelected(active);
            item.icon().setImageTintList(ColorStateList.valueOf(tint));
            item.caption().setTextColor(tint);
            item.caption().setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        });
    }

    private void addTab(LinearLayout row, Tab tab, Consumer<Tab> onTab) {
        Context context = getContext();
        LinearLayout target = new LinearLayout(context);
        target.setOrientation(LinearLayout.VERTICAL);
        target.setGravity(Gravity.CENTER);
        target.setContentDescription(context.getText(tab.label));
        target.setFocusable(true);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        target.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28FFFFFF), null, mask));
        target.setOnClickListener(button -> onTab.accept(tab));

        ImageView icon = new ImageView(context);
        icon.setImageResource(tab.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = dp(22);
        target.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView caption = new TextView(context);
        caption.setText(tab.label);
        caption.setTextSize(12);
        caption.setMaxLines(1);
        caption.setGravity(Gravity.CENTER);
        caption.setIncludeFontPadding(false);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = dp(1);
        target.addView(caption, captionParams);

        tabs.put(tab, new TabItem(target, icon, caption));
        row.addView(target, slot());
    }

    private static LinearLayout.LayoutParams slot() {
        return new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
    }

    private int dp(int units) {
        return Math.round(units * getResources().getDisplayMetrics().density);
    }
}
