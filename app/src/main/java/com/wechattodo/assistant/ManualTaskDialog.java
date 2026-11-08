package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

final class ManualTaskDialog {
    record Entry(String body, Long due, TaskCategory category) {}

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);
    private final Activity activity;
    private Long due;

    ManualTaskDialog(Activity activity) {
        this.activity = activity;
    }

    void show(TaskCategory preferred, Consumer<Entry> onSave) {
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
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        add(fields, input, 0);

        TextView categoryLabel = new TextView(activity);
        categoryLabel.setText(R.string.manual_category);
        add(fields, categoryLabel, 12);
        Spinner categories = new Spinner(activity);
        categories.setId(View.generateViewId());
        categoryLabel.setLabelFor(categories.getId());
        String[] options = {activity.getString(R.string.manual_category_auto), activity.getString(R.string.daily_tasks),
                activity.getString(R.string.work_tasks)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categories.setAdapter(adapter);
        categories.setMinimumHeight(dp(48));
        categories.setSelection(preferred == TaskCategory.DAILY ? 1 : preferred == TaskCategory.WORK ? 2 : 0);
        add(fields, categories, 0);

        Button deadline = button(R.string.manual_deadline);
        Button clear = button(R.string.manual_deadline_clear);
        clear.setVisibility(View.GONE);
        deadline.setOnClickListener(view -> chooseDeadline(deadline, clear));
        clear.setOnClickListener(view -> {
            due = null;
            deadline.setText(R.string.manual_deadline);
            clear.setVisibility(View.GONE);
        });
        add(fields, deadline, 8);
        add(fields, clear, 0);

        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.manual_add)
                .setView(scroll).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.manual_save, null).create();
        dialog.setOnShowListener(view -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(save -> {
            String body = input.getText().toString().strip();
            if (body.isBlank()) {
                input.setError(activity.getString(R.string.manual_empty_error));
                input.requestFocus();
                return;
            }
            TaskCategory category = switch (categories.getSelectedItemPosition()) {
                case 1 -> TaskCategory.DAILY;
                case 2 -> TaskCategory.WORK;
                default -> null;
            };
            onSave.accept(new Entry(body, due, category));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void chooseDeadline(Button deadline, Button clear) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime initial = due == null ? now.plusHours(1).withSecond(0).withNano(0)
                : Instant.ofEpochMilli(due).atZone(now.getZone());
        DatePickerDialog date = new DatePickerDialog(activity, (picker, year, month, day) -> {
            LocalDate selected = LocalDate.of(year, month + 1, day);
            new TimePickerDialog(activity, (time, hour, minute) -> {
                ZonedDateTime chosen = selected.atTime(hour, minute).atZone(now.getZone());
                due = chosen.toInstant().toEpochMilli();
                deadline.setText(activity.getString(R.string.manual_deadline_value, DATE.format(chosen)));
                clear.setVisibility(View.VISIBLE);
            }, initial.getHour(), initial.getMinute(), true).show();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        date.getDatePicker().setMinDate(now.toLocalDate().atStartOfDay(now.getZone()).toInstant().toEpochMilli());
        date.show();
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
