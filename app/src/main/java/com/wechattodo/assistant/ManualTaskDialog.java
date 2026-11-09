package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class ManualTaskDialog {
    static final int MAX_BODY_LENGTH = 2000;
    private static final DateTimeFormatter PICKER_DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE);
    record Entry(String body, Long due, TaskCategory category) {}

    private final Activity activity;
    private Long due;

    ManualTaskDialog(Activity activity) {
        this.activity = activity;
    }

    void show(TaskCategory preferred, Consumer<Entry> onSave) {
        showForm(null, null, preferred, false, R.string.manual_add, R.string.manual_save, onSave);
    }

    void confirmClipboard(String body, Long deadline, Consumer<Entry> onSave) {
        showForm(body, deadline, null, false, R.string.clipboard_add, R.string.clipboard_save, onSave);
    }

    void edit(TaskStore.Task task, Consumer<Entry> onSave) {
        showForm(task.body(), task.due(), task.manualCategory(), true, R.string.edit_task, R.string.manual_save, onSave);
    }

    void postpone(Long currentDeadline, Consumer<Long> onSave) {
        ZonedDateTime now = ZonedDateTime.now();
        long earliest = Math.max(now.toInstant().toEpochMilli(), currentDeadline == null ? 0 : currentDeadline);
        ZonedDateTime minimum = Instant.ofEpochMilli(earliest).atZone(now.getZone());
        ZonedDateTime initial = minimum.plusHours(1).withSecond(0).withNano(0);
        pickDeadline(initial, minimum.toLocalDate(), chosen -> {
            if (chosen > Math.max(earliest, System.currentTimeMillis())) {
                onSave.accept(chosen);
            } else {
                Toast.makeText(activity, R.string.postpone_time_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showForm(String body, Long deadlineAt, TaskCategory preferred, boolean editing, int title,
                          int saveLabel, Consumer<Entry> onSave) {
        due = deadlineAt;
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(6), dp(20), dp(8));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);

        EditText input = new EditText(activity);
        input.setHint(R.string.manual_body_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_BODY_LENGTH)});
        input.setText(body);
        add(fields, input, 0);

        Spinner categories = editing ? null : categoryPicker(fields, preferred);

        Button deadline = button(R.string.manual_deadline);
        Button clear = button(R.string.manual_deadline_clear);
        showDeadline(deadline, clear);
        deadline.setOnClickListener(view -> chooseDeadline(deadline, clear));
        clear.setOnClickListener(view -> {
            due = null;
            showDeadline(deadline, clear);
        });
        add(fields, deadline, 8);
        add(fields, clear, 0);

        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title)
                .setView(scroll).setNegativeButton(R.string.cancel, null).setPositiveButton(saveLabel, null).create();
        if (title == R.string.clipboard_add) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        dialog.setOnShowListener(view -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(save -> {
            String editedBody = input.getText().toString().strip();
            if (editedBody.isBlank()) {
                input.setError(activity.getString(R.string.manual_empty_error));
                input.requestFocus();
                return;
            }
            TaskCategory category = categories == null ? preferred : switch (categories.getSelectedItemPosition()) {
                case 1 -> TaskCategory.DAILY;
                case 2 -> TaskCategory.WORK;
                default -> null;
            };
            onSave.accept(new Entry(editedBody, due, category));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private Spinner categoryPicker(LinearLayout fields, TaskCategory preferred) {
        TextView label = new TextView(activity);
        label.setText(R.string.manual_category);
        add(fields, label, 12);
        Spinner categories = new Spinner(activity);
        categories.setId(View.generateViewId());
        label.setLabelFor(categories.getId());
        String[] options = {activity.getString(R.string.manual_category_auto), activity.getString(R.string.daily_tasks),
                activity.getString(R.string.work_tasks)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categories.setAdapter(adapter);
        categories.setMinimumHeight(dp(48));
        categories.setSelection(preferred == TaskCategory.DAILY ? 1 : preferred == TaskCategory.WORK ? 2 : 0);
        add(fields, categories, 0);
        return categories;
    }

    private void showDeadline(Button deadline, Button clear) {
        if (due == null) deadline.setText(R.string.manual_deadline);
        else deadline.setText(activity.getString(R.string.manual_deadline_value, TaskTime.full(due, ZonedDateTime.now().getZone())));
        clear.setVisibility(due == null ? View.GONE : View.VISIBLE);
    }

    private void chooseDeadline(Button deadline, Button clear) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime existing = due == null ? now.plusHours(1).withSecond(0).withNano(0)
                : Instant.ofEpochMilli(due).atZone(now.getZone());
        ZonedDateTime initial = existing.toLocalDate().isBefore(now.toLocalDate()) ? now.plusHours(1) : existing;
        pickDeadline(initial, now.toLocalDate(), chosen -> {
            due = chosen;
            showDeadline(deadline, clear);
        });
    }

    private void pickDeadline(ZonedDateTime initial, LocalDate minimum, Consumer<Long> onChosen) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);

        TextView dateLabel = new TextView(activity);
        dateLabel.setText(PICKER_DATE.format(initial));
        dateLabel.setTextSize(16);
        dateLabel.setTextColor(Color.WHITE);
        dateLabel.setBackgroundColor(0xFF116654);
        dateLabel.setGravity(Gravity.CENTER_VERTICAL);
        dateLabel.setMinHeight(dp(44));
        dateLabel.setPadding(dp(16), dp(6), dp(16), dp(6));
        add(content, dateLabel, 0);

        CalendarView calendar = new CalendarView(activity);
        calendar.setMinDate(minimum.atStartOfDay(initial.getZone()).toInstant().toEpochMilli());
        calendar.setDate(initial.toInstant().toEpochMilli(), false, false);
        AtomicReference<LocalDate> selectedDate = new AtomicReference<>(initial.toLocalDate());
        calendar.setOnDateChangeListener((view, year, month, day) -> {
            LocalDate date = LocalDate.of(year, month + 1, day);
            selectedDate.set(date);
            dateLabel.setText(PICKER_DATE.format(date));
        });
        add(content, calendar, 0);

        NumberPicker hours = wheel(R.string.deadline_hours, 23, initial.getHour());
        NumberPicker minutes = wheel(R.string.deadline_minutes, 59, initial.getMinute());
        LinearLayout time = new LinearLayout(activity);
        time.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        time.setPadding(0, dp(4), 0, 0);
        time.addView(wheelColumn(R.string.deadline_hours, hours));
        TextView colon = new TextView(activity);
        colon.setText(":");
        colon.setTextSize(24);
        colon.setTextColor(0xFF17332B);
        colon.setGravity(Gravity.CENTER);
        colon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        time.addView(colon, new LinearLayout.LayoutParams(dp(20), dp(108)));
        time.addView(wheelColumn(R.string.deadline_minutes, minutes));
        add(content, time, 0);

        new AlertDialog.Builder(activity).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    long chosen = selectedDate.get().atTime(hours.getValue(), minutes.getValue())
                            .atZone(initial.getZone()).toInstant().toEpochMilli();
                    onChosen.accept(chosen);
                })
                .show();
    }

    private NumberPicker wheel(int label, int maximum, int value) {
        NumberPicker picker = new NumberPicker(activity);
        picker.setMinValue(0);
        picker.setMaxValue(maximum);
        picker.setFormatter(number -> String.format(Locale.ROOT, "%02d", number));
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setContentDescription(activity.getText(label));
        picker.setSelectionDividerHeight(0);
        picker.setTextColor(0xFF17332B);
        picker.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26,
                activity.getResources().getDisplayMetrics()));
        return picker;
    }

    private LinearLayout wheelColumn(int label, NumberPicker picker) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextColor(0xFF475D55);
        title.setGravity(Gravity.CENTER);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        add(column, title, 0);
        column.addView(picker, new LinearLayout.LayoutParams(dp(72), dp(108)));
        return column;
    }

    private Button button(int text) {
        Button button = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        button.setText(text);
        button.setMinHeight(dp(48));
        return button;
    }

    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private int dp(int units) {
        return Math.round(units * activity.getResources().getDisplayMetrics().density);
    }
}
