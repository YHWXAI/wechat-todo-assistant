package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.function.Consumer;

final class DeadlinePickerDialog {
    private static final int INK = 0xFF17332B;
    private static final int[] DATE_LABELS = {R.string.deadline_year, R.string.deadline_month, R.string.deadline_day};
    private final Activity activity;
    private final ZonedDateTime initial;
    private final LocalDate minimum;
    private LocalDate date;
    private final Button[] dateButtons = new Button[3];

    DeadlinePickerDialog(Activity activity, ZonedDateTime initial, LocalDate minimum) {
        this.activity = activity;
        this.initial = initial;
        this.minimum = minimum;
        date = initial.toLocalDate();
    }

    void show(Consumer<Long> onChosen) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFFEAF0F3);
        panel.setPadding(dp(14), dp(10), 0, dp(10));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_LTR);
        LinearLayout dates = new LinearLayout(activity);
        dates.setOrientation(LinearLayout.VERTICAL);
        row.addView(dates, new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < DATE_LABELS.length; i++) {
            int part = i;
            Button button = new Button(activity, null, android.R.attr.borderlessButtonStyle);
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            button.setTextColor(INK);
            button.setTextSize(18);
            button.setPadding(0, dp(4), 0, dp(4));
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(0xFFF7FAFB);
            surface.setCornerRadius(dp(14));
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25116654), surface, null));
            button.setOnClickListener(view -> chooseDatePart(part));
            LinearLayout.LayoutParams tile = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
            tile.topMargin = i == 0 ? 0 : dp(6);
            dates.addView(button, tile);
            dateButtons[i] = button;
        }
        showDate();

        TimeDial dial = new TimeDial(activity, initial.getHour(), initial.getMinute());
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        dialParams.setMarginStart(dp(5));
        row.addView(dial, dialParams);
        panel.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(activity);
        hint.setText(R.string.deadline_dial_hint);
        hint.setTextSize(12);
        hint.setTextColor(0xFF475D55);
        hint.setGravity(Gravity.END);
        hint.setPadding(0, dp(5), dp(16), 0);
        panel.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);
        new AlertDialog.Builder(activity).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    long chosen = date.atTime(dial.hour(), dial.minute())
                            .atZone(initial.getZone()).toInstant().toEpochMilli();
                    onChosen.accept(chosen);
                })
                .show();
    }

    private void showDate() {
        int[] values = {date.getYear(), date.getMonthValue(), date.getDayOfMonth()};
        for (int i = 0; i < values.length; i++) {
            Button button = dateButtons[i];
            String label = activity.getString(DATE_LABELS[i]);
            String value = String.format(Locale.ROOT, i == 0 ? "%04d" : "%02d", values[i]);
            SpannableString text = new SpannableString(label + "\n" + value);
            text.setSpan(new RelativeSizeSpan(0.65f), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), label.length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            button.setText(text);
        }
    }

    private void chooseDatePart(int part) {
        NumberPicker picker = new NumberPicker(activity);
        int start;
        int end;
        int value;
        if (part == 0) {
            start = minimum.getYear();
            end = Math.max(2100, date.getYear());
            value = date.getYear();
        } else if (part == 1) {
            start = date.getYear() == minimum.getYear() ? minimum.getMonthValue() : 1;
            end = 12;
            value = date.getMonthValue();
        } else {
            start = date.getYear() == minimum.getYear() && date.getMonth() == minimum.getMonth()
                    ? minimum.getDayOfMonth() : 1;
            end = date.lengthOfMonth();
            value = date.getDayOfMonth();
        }
        picker.setMaxValue(end);
        picker.setMinValue(start);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        new AlertDialog.Builder(activity).setTitle(DATE_LABELS[part]).setView(picker)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int chosen = picker.getValue();
                    LocalDate updated = switch (part) {
                        case 0 -> date.withYear(chosen);
                        case 1 -> date.withMonth(chosen);
                        default -> date.withDayOfMonth(chosen);
                    };
                    date = updated.isBefore(minimum) ? minimum : updated;
                    showDate();
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
