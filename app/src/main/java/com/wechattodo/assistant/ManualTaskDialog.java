package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

final class ManualTaskDialog {
    static final int MAX_BODY_LENGTH = 2000;
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
        DatePickerDialog date = new DatePickerDialog(activity, (picker, year, month, day) -> {
            LocalDate selected = LocalDate.of(year, month + 1, day);
            new TimePickerDialog(activity, (time, hour, minute) -> {
                ZonedDateTime chosen = selected.atTime(hour, minute).atZone(initial.getZone());
                onChosen.accept(chosen.toInstant().toEpochMilli());
            }, initial.getHour(), initial.getMinute(), true).show();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        date.getDatePicker().setMinDate(minimum.atStartOfDay(initial.getZone()).toInstant().toEpochMilli());
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
