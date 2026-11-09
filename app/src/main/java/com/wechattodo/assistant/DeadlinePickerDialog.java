package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class DeadlinePickerDialog {
    private static final int INK = 0xFF17332B;
    private static final int ACCENT = 0xFF116654;
    private static final int TIME_TEXT_SP = 44;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE);
    private final Activity activity;
    private final ZonedDateTime initial;
    private final LocalDate minimum;
    private LocalDate date;

    DeadlinePickerDialog(Activity activity, ZonedDateTime initial, LocalDate minimum) {
        this.activity = activity;
        this.initial = initial;
        this.minimum = minimum;
        date = initial.toLocalDate();
    }

    void show(Consumer<Long> onChosen) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(8), dp(16), dp(4));

        int calendarSize = dp(48);
        int calendarMargin = dp(4);
        FrameLayout dateRow = new FrameLayout(activity);
        dateRow.setBackground(surface(0xFFF2F6F4, 14));
        TextView dateLabel = new TextView(activity);
        dateLabel.setText(DATE.format(date));
        dateLabel.setTextColor(INK);
        dateLabel.setTextSize(16);
        dateLabel.setSingleLine(true);
        dateLabel.setEllipsize(TextUtils.TruncateAt.END);
        dateLabel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int dateSideSpace = calendarSize + calendarMargin;
        dateParams.setMargins(dateSideSpace, 0, dateSideSpace, 0);
        dateRow.addView(dateLabel, dateParams);

        ImageButton calendarButton = new ImageButton(activity);
        TypedValue ripple = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        calendarButton.setBackgroundResource(ripple.resourceId);
        calendarButton.setImageResource(R.drawable.ic_nav_daily);
        calendarButton.setImageTintList(ColorStateList.valueOf(ACCENT));
        calendarButton.setContentDescription(activity.getString(R.string.deadline_calendar_open));
        calendarButton.setOnClickListener(view -> chooseDate(dateLabel));
        FrameLayout.LayoutParams calendarParams = new FrameLayout.LayoutParams(
                calendarSize, calendarSize, Gravity.END | Gravity.CENTER_VERTICAL);
        calendarParams.setMarginEnd(calendarMargin);
        dateRow.addView(calendarButton, calendarParams);
        panel.addView(dateRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        NumberPicker hours = wheel(23, initial.getHour(), R.string.deadline_hours);
        NumberPicker minutes = wheel(59, initial.getMinute(), R.string.deadline_minutes);
        LinearLayout time = new LinearLayout(activity);
        time.setGravity(Gravity.CENTER);
        time.addView(hours, new LinearLayout.LayoutParams(dp(88), dp(156)));

        TextView separator = new TextView(activity);
        separator.setText(":");
        separator.setTextColor(INK);
        separator.setTextSize(TIME_TEXT_SP);
        separator.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        separator.setGravity(Gravity.CENTER);
        separator.setImportantForAccessibility(TextView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        time.addView(separator, new LinearLayout.LayoutParams(dp(28), dp(156)));
        time.addView(minutes, new LinearLayout.LayoutParams(dp(88), dp(156)));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(8);
        panel.addView(time, timeParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);
        new AlertDialog.Builder(activity).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        onChosen.accept(date.atTime(hours.getValue(), minutes.getValue())
                                .atZone(initial.getZone()).toInstant().toEpochMilli()))
                .show();
    }

    private NumberPicker wheel(int maximum, int value, int description) {
        NumberPicker picker = new NumberPicker(activity);
        picker.setMinValue(0);
        picker.setMaxValue(maximum);
        picker.setFormatter(number -> number < 10 ? "0" + number : Integer.toString(number));
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setSelectionDividerHeight(0);
        picker.setTextColor(INK);
        picker.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TIME_TEXT_SP,
                activity.getResources().getDisplayMetrics()));
        picker.setContentDescription(activity.getString(description));
        return picker;
    }

    private void chooseDate(TextView dateLabel) {
        CalendarView calendar = new CalendarView(activity);
        calendar.setFirstDayOfWeek(Calendar.SUNDAY);
        long selected = date.atStartOfDay(initial.getZone()).toInstant().toEpochMilli();
        if (selected > calendar.getMaxDate()) calendar.setMaxDate(selected);
        calendar.setMinDate(minimum.atStartOfDay(initial.getZone()).toInstant().toEpochMilli());
        calendar.setDate(selected, false, false);
        AtomicReference<LocalDate> pickedDate = new AtomicReference<>(date);
        calendar.setOnDateChangeListener((view, year, month, day) ->
                pickedDate.set(LocalDate.of(year, month + 1, day)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setPadding(dp(8), dp(12), dp(8), 0);
        scroll.addView(calendar);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.deadline_calendar_done, (window, which) -> {
                    date = pickedDate.get();
                    dateLabel.setText(DATE.format(date));
                })
                .show();
        var window = dialog.getWindow();
        window.setBackgroundDrawable(new InsetDrawable(
                activity.getDrawable(R.drawable.dialog_surface), dp(12), 0, dp(12), dp(8)));
        window.setGravity(Gravity.BOTTOM);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
