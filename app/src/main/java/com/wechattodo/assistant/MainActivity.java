package com.wechattodo.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFFF3F7F5;
    private static final int INK = 0xFF17332B;
    private static final int SUBTLE = 0xFF475D55;
    private static final int GREEN = 0xFF116654;
    private static final int OVERDUE = 0xFF9A3424;
    private static final int WHITE = 0xFFFFFFFF;
    private static final String SETTINGS_OPEN = "settings_open";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showTasks();
        }
    };
    private final OnBackInvokedCallback closeSettings = () -> showSettings(false);
    private View homePage;
    private View settingsPage;
    private boolean settingsOpen;
    private TextView status;
    private Button listenerButton;
    private Button remindersButton;
    private TextView count;
    private LinearLayout taskList;
    private TextView chatReaderStatus;
    private Button chatReaderButton;
    private Button chatReaderSettings;
    private UpdateController updates;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        homePage = createHomePage();
        settingsPage = createSettingsPage();
        settingsPage.setVisibility(View.GONE);
        root.addView(homePage);
        root.addView(settingsPage);
        setContentView(root);
        if (savedState != null && savedState.getBoolean(SETTINGS_OPEN)) {
            showSettings(true);
        }
    }

    private View createHomePage() {
        LinearLayout screen = column();
        screen.setAccessibilityPaneTitle(getText(R.string.app_name));
        LinearLayout header = header(screen);
        LinearLayout titles = column();
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        add(titles, label(getString(R.string.app_name), 28, INK, true), 0);
        add(titles, label(getString(R.string.app_subtitle, BuildConfig.VERSION_NAME), 14, SUBTLE, false), 4);
        header.addView(button(getString(R.string.settings_title), view -> showSettings(true)));

        LinearLayout page = scrollBody(screen);
        count = label("", 20, INK, true);
        add(page, count, 12);
        taskList = column();
        add(page, taskList, 0);
        add(page, label("识别和保存均在手机上进行。到期提醒可能受系统省电策略延迟。",
                13, SUBTLE, false), 14);
        return screen;
    }

    private View createSettingsPage() {
        LinearLayout screen = column();
        screen.setAccessibilityPaneTitle(getText(R.string.settings_title));
        LinearLayout header = header(screen);
        header.addView(button(getString(R.string.back_to_tasks), view -> showSettings(false)));
        TextView title = label(getString(R.string.settings_title), 24, INK, true);
        title.setAccessibilityHeading(true);
        header.addView(title);
        LinearLayout page = scrollBody(screen);

        LinearLayout permissions = card();
        add(page, permissions, 8);
        add(permissions, label("权限状态", 17, INK, true), 0);
        status = label("", 14, SUBTLE, false);
        add(permissions, status, 6);
        listenerButton = button("开启消息通知读取", v -> openSettings(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        add(permissions, listenerButton, 8);
        remindersButton = button("开启到期提醒", v -> requestReminderPermission());
        add(permissions, remindersButton, 4);

        LinearLayout monitoring = card();
        add(page, monitoring, 12);
        add(monitoring, label("消息监测", 17, INK, true), 0);
        add(monitoring, label("选择通讯应用，添加要监测的群，并填写你在群里的昵称。", 14, SUBTLE, false), 6);
        add(monitoring, button("管理监测应用与会话", view -> startActivity(
                new Intent(this, MonitoringActivity.class))), 4);

        LinearLayout reader = card();
        add(page, reader, 12);
        add(reader, label("自动识别微信聊天", 17, INK, true), 0);
        chatReaderStatus = label("", 14, SUBTLE, false);
        add(reader, chatReaderStatus, 6);
        chatReaderButton = button("开启自动识别", v -> toggleChatRead());
        add(reader, chatReaderButton, 4);
        chatReaderSettings = button("打开无障碍设置", v -> openAccessibilitySettings());
        add(reader, chatReaderSettings, 0);

        LinearLayout updater = card();
        add(page, updater, 12);
        add(updater, label("应用更新", 17, INK, true), 0);
        TextView updateStatus = label("", 14, SUBTLE, false);
        add(updater, updateStatus, 6);
        Button check = button("检查并下载更新", null);
        Button install = button("安装已下载的更新", null);
        add(updater, check, 4);
        add(updater, install, 0);
        updates = new UpdateController(this, updateStatus, check, install, () -> settingsOpen);
        add(updater, button("用浏览器下载", view -> updates.openDownloadPage()), 0);
        return screen;
    }

    private void showSettings(boolean show) {
        if (settingsOpen == show) {
            return;
        }
        settingsOpen = show;
        homePage.setVisibility(show ? View.GONE : View.VISIBLE);
        settingsPage.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, closeSettings);
            showPermissions();
        } else {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(closeSettings);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(SETTINGS_OPEN, settingsOpen);
    }

    @Override
    protected void onDestroy() {
        if (settingsOpen) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(closeSettings);
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(changes, new IntentFilter(TaskStore.CHANGED), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsOpen) {
            showPermissions();
        }
        showTasks();
        Reminders.scheduleNext(this);
        updates.onResume();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(changes);
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1) {
            showPermissions();
            Reminders.scheduleNext(this);
        }
    }

    private void showPermissions() {
        boolean listener = hasListenerAccess();
        boolean reminders = Reminders.canNotify(this);
        status.setText(getString(R.string.permission_status,
                getString(listener ? R.string.enabled : R.string.disabled),
                getString(reminders ? R.string.enabled : R.string.disabled)));
        listenerButton.setVisibility(listener ? View.GONE : View.VISIBLE);
        remindersButton.setVisibility(reminders ? View.GONE : View.VISIBLE);
        showChatReaderStatus();
    }

    private void showChatReaderStatus() {
        boolean enabled = WeChatChatReader.isEnabled(this);
        boolean authorized = WeChatChatReader.hasAccess(this);
        chatReaderStatus.setText(!enabled ? R.string.auto_read_off
                : authorized ? R.string.auto_read_on : R.string.auto_read_waiting);
        chatReaderButton.setText(enabled ? R.string.auto_read_stop : R.string.auto_read_start);
        chatReaderSettings.setVisibility(enabled && !authorized ? View.VISIBLE : View.GONE);
    }

    private boolean hasListenerAccess() {
        ComponentName component = new ComponentName(this, WeChatListener.class);
        return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(component);
    }

    private void requestReminderPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!getPreferences(MODE_PRIVATE).getBoolean("notification_requested", false)
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                return;
            }
        }
        boolean appEnabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        Intent settings = new Intent(appEnabled ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                : Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        if (appEnabled) {
            settings.putExtra(Settings.EXTRA_CHANNEL_ID, Reminders.CHANNEL);
        }
        openSettings(settings);
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, "请在系统设置中手动开启权限", Toast.LENGTH_LONG).show();
        }
    }

    private void toggleChatRead() {
        if (WeChatChatReader.isEnabled(this)) {
            WeChatChatReader.setEnabled(this, false);
            showChatReaderStatus();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.auto_read_start)
                .setMessage("开启后，你打开或滚动微信聊天时，助手会通过无障碍权限识别当前可见的消息。"
                        + "你可以在微信中确认要保存到手机上的待办。"
                        + "请先在「消息监测」添加要识别的微信会话。"
                        + "你可以随时在这里关闭自动识别。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开启", (dialog, which) -> {
                    WeChatChatReader.setEnabled(this, true);
                    showChatReaderStatus();
                    if (!WeChatChatReader.hasAccess(this)) {
                        openAccessibilitySettings();
                    }
                })
                .show();
    }

    private void openAccessibilitySettings() {
        openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showTasks() {
        List<TaskStore.Task> tasks = TaskStore.get(this).list();
        List<TaskStore.Task> daily = new ArrayList<>();
        List<TaskStore.Task> work = new ArrayList<>();
        for (TaskStore.Task task : tasks) {
            (TaskCategory.classify(task.body(), task.source()) == TaskCategory.WORK ? work : daily).add(task);
        }
        taskList.removeAllViews();
        int dailyOpen = showTaskGroup(R.string.daily_tasks, daily);
        int workOpen = showTaskGroup(R.string.work_tasks, work);
        count.setText(getString(R.string.open_task_count, dailyOpen + workOpen));
        if (tasks.isEmpty()) {
            add(taskList, label("在右上角「设置」开启通知读取，并在「消息监测」添加要识别的会话。"
                    + "\n例如「请明天下午3点发送设计稿」。", 14, SUBTLE, false), 16);
        }
    }

    private int showTaskGroup(int title, List<TaskStore.Task> tasks) {
        int open = 0;
        for (TaskStore.Task task : tasks) {
            if (!task.done()) {
                open++;
            }
        }
        TextView heading = label(getString(R.string.task_group_count, getString(title), open), 17, INK, true);
        heading.setAccessibilityHeading(true);
        add(taskList, heading, 14);
        if (tasks.isEmpty()) {
            add(taskList, label(getString(R.string.empty_task_group), 14, SUBTLE, false), 6);
        }
        for (TaskStore.Task task : tasks) {
            add(taskList, taskCard(task), 8);
        }
        return open;
    }

    private LinearLayout taskCard(TaskStore.Task task) {
        LinearLayout card = card();
        add(card, label(task.source(), 13, GREEN, true), 0);
        TextView body = label(task.body(), 17, INK, false);
        if (task.done()) {
            body.setPaintFlags(body.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        add(card, body, 5);

        Long due = task.due();
        boolean overdue = due != null && !task.done() && due <= System.currentTimeMillis();
        String detail = due == null ? "未识别到截止时间" : "截止 " + formatDue(due);
        if (task.done()) {
            detail = "已完成  ·  " + detail;
        } else if (overdue) {
            detail = "已到期  ·  " + detail;
        }
        add(card, label(detail, 13, overdue ? OVERDUE : SUBTLE, false), 7);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        if (!task.done()) {
            actions.addView(button("完成", v -> {
                TaskStore.get(this).complete(task.id());
                afterTaskChanged(task.id());
            }));
        }
        actions.addView(button("删除", v -> new AlertDialog.Builder(this)
                .setTitle("删除这项待办？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    TaskStore.get(this).delete(task.id());
                    afterTaskChanged(task.id());
                })
                .show()));
        add(card, actions, 4);
        return card;
    }

    private void afterTaskChanged(long id) {
        Reminders.dismiss(this, id);
        Reminders.scheduleNext(this);
        showTasks();
    }

    private static String formatDue(long time) {
        return DATE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    private LinearLayout header(LinearLayout screen) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(8));
        add(screen, header, 0);
        return header;
    }

    private LinearLayout scrollBody(LinearLayout screen) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        screen.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout page = column();
        page.setPadding(dp(20), 0, dp(20), dp(20));
        scroll.addView(page);
        return page;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = column();
        layout.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(WHITE);
        surface.setCornerRadius(dp(14));
        layout.setBackground(surface);
        return layout;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button button(String text, View.OnClickListener click) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(text);
        button.setTextColor(GREEN);
        button.setMinHeight(dp(48));
        button.setOnClickListener(click);
        return button;
    }

    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private int dp(int units) {
        return Math.round(units * getResources().getDisplayMetrics().density);
    }
}
