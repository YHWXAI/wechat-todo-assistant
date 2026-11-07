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
import android.widget.Button;
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
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showTasks();
        }
    };
    private TextView status;
    private Button listenerButton;
    private Button remindersButton;
    private TextView count;
    private LinearLayout taskList;
    private TextView chatReaderStatus;
    private Button chatReaderButton;
    private UpdateController updates;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        LinearLayout page = column();
        page.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(page);
        setContentView(scroll);

        add(page, label("微信待办助手", 28, INK, true), 0);
        add(page, label(getString(R.string.app_subtitle, BuildConfig.VERSION_NAME), 14, SUBTLE, false), 4);

        LinearLayout permissions = card();
        add(page, permissions, 20);
        add(permissions, label("权限状态", 17, INK, true), 0);
        status = label("", 14, SUBTLE, false);
        add(permissions, status, 6);
        listenerButton = button("开启微信通知读取", v -> openSettings(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        add(permissions, listenerButton, 8);
        remindersButton = button("开启到期提醒", v -> requestReminderPermission());
        add(permissions, remindersButton, 4);

        LinearLayout reader = card();
        add(page, reader, 12);
        add(reader, label("读取当前微信聊天", 17, INK, true), 0);
        chatReaderStatus = label("", 14, SUBTLE, false);
        add(reader, chatReaderStatus, 6);
        chatReaderButton = button("准备读取", v -> startChatRead());
        add(reader, chatReaderButton, 4);

        count = label("", 20, INK, true);
        add(page, count, 20);
        taskList = column();
        add(page, taskList, 0);
        add(page, label("识别和保存均在手机上进行。到期提醒可能受系统省电策略延迟。",
                13, SUBTLE, false), 14);

        LinearLayout updater = card();
        add(page, updater, 16);
        add(updater, label("应用更新", 17, INK, true), 0);
        TextView updateStatus = label("", 14, SUBTLE, false);
        add(updater, updateStatus, 6);
        Button check = button("检查并下载更新", null);
        Button install = button("安装已下载的更新", null);
        add(updater, check, 4);
        add(updater, install, 0);
        updates = new UpdateController(this, updateStatus, check, install);
        add(updater, button("用浏览器下载", view -> updates.openDownloadPage()), 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(changes, new IntentFilter(TaskStore.CHANGED), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showPermissions();
        showTasks();
        Reminders.scheduleNext(this);
        updates.onResume();
        WeChatChatReader.Preview preview = WeChatChatReader.takePreview();
        if (preview != null) {
            showChatPreview(preview);
        }
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
        boolean reader = WeChatChatReader.isReady();
        chatReaderStatus.setText(reader ? "已授权。准备后切到微信聊天，再点「读取当前聊天」。"
                : "你可以授权无障碍读取，在微信打开聊天后主动提取待办。");
        chatReaderButton.setText(reader ? "准备读取" : "开启聊天界面读取");
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

    private void startChatRead() {
        if (WeChatChatReader.armReader()) {
            Toast.makeText(this, "请在 90 秒内切到微信聊天，点右上方的「读取当前聊天」。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("授权读取当前微信聊天")
                .setMessage("安卓无障碍权限允许应用查看界面。本助手只在你先点「准备读取」、再在微信点「读取当前聊天」后查看当前可见聊天。"
                        + "候选内容由你勾选，保存的待办留在手机上。你随时可以在系统设置里关闭权限。")
                .setNegativeButton("取消", null)
                .setPositiveButton("打开无障碍设置", (dialog, which) -> openSettings(
                        new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show();
    }

    private void showChatPreview(WeChatChatReader.Preview preview) {
        List<Long> deadlines = new ArrayList<>();
        String[] entries = new String[preview.messages().size()];
        for (int i = 0; i < entries.length; i++) {
            String message = preview.messages().get(i);
            Long due = TaskRules.dueAt(message, preview.scannedAt());
            deadlines.add(due);
            entries[i] = message + (due == null ? "" : "\n截止 " + formatDue(due));
        }
        boolean[] selected = new boolean[entries.length];
        LinearLayout heading = column();
        heading.setPadding(dp(22), dp(18), dp(22), dp(10));
        add(heading, label(preview.chat(), 19, INK, true), 0);
        add(heading, label("勾选要保存的待办。明天等相对日期以扫描当天为准，请核对旧消息的截止时间。",
                14, SUBTLE, false), 4);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(heading)
                .setMultiChoiceItems(entries, selected, (window, index, checked) -> {
                    selected[index] = checked;
                    ((AlertDialog) window).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(hasSelection(selected));
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("保存选中的待办", (window, which) -> saveChatTasks(preview, deadlines, selected))
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private static boolean hasSelection(boolean[] selected) {
        for (boolean item : selected) {
            if (item) {
                return true;
            }
        }
        return false;
    }

    private void saveChatTasks(WeChatChatReader.Preview preview, List<Long> deadlines, boolean[] selected) {
        TaskStore store = TaskStore.get(this);
        int added = 0;
        int duplicate = 0;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                if (store.addFromChat(preview.chat(), preview.messages().get(i), deadlines.get(i))) {
                    added++;
                } else {
                    duplicate++;
                }
            }
        }
        if (added > 0) {
            Reminders.scheduleNext(this);
            showTasks();
        }
        Toast.makeText(this, getString(R.string.read_result, added, duplicate), Toast.LENGTH_LONG).show();
    }

    private void showTasks() {
        List<TaskStore.Task> tasks = TaskStore.get(this).list();
        int open = 0;
        taskList.removeAllViews();
        for (TaskStore.Task task : tasks) {
            add(taskList, taskCard(task), 8);
            if (!task.done()) {
                open++;
            }
        }
        count.setText(getString(R.string.open_task_count, open));
        if (tasks.isEmpty()) {
            LinearLayout empty = card();
            add(taskList, empty, 8);
            add(empty, label("暂无待办", 17, INK, true), 0);
            add(empty, label("开启通知读取后，让微信联系人发来一条会显示通知的消息。"
                    + "\n例如「请明天下午3点发送设计稿」。", 14, SUBTLE, false), 6);
        }
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
