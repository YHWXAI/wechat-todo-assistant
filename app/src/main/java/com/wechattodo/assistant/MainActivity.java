package com.wechattodo.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.wechattodo.assistant.BottomNavigation.Tab;
import com.wechattodo.assistant.MonitoringRules.App;
import com.wechattodo.assistant.TaskCardView.Mode;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFFF3F7F5;
    private static final int INK = 0xFF17332B;
    private static final int SUBTLE = 0xFF475D55;
    private static final int GREEN = 0xFF116654;
    private static final int WHITE = 0xFFFFFFFF;
    private static final String SELECTED_TAB = "selected_tab";
    private static final String FEATURED_TASK = "featured_task";
    private static final String FEATURED_EXPANDED = "featured_expanded";
    private static final String COMPACT_TASKS = "compact_tasks";
    private static final String SORT_BY_DEADLINE = "sort_by_deadline";
    private static final String LAST_CLIPBOARD_TIMESTAMP = "last_clipboard_timestamp";
    private static final long NO_FEATURED_TASK = -1;

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showTasks();
        }
    };
    private final OnBackInvokedCallback returnHome = () -> selectTab(Tab.HOME);
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable updateCountdowns = this::refreshCountdowns;
    private final List<TaskCardView> taskCards = new ArrayList<>();
    private final EnumMap<App, Drawable.ConstantState> sourceIcons = new EnumMap<>(App.class);
    private boolean resumed;
    private boolean clipboardCheckPending;
    private View taskPage;
    private View settingsPage;
    private BottomNavigation navigation;
    private Tab selectedTab = Tab.HOME;
    private Tab lastTaskTab = Tab.HOME;
    private long featuredTaskId = NO_FEATURED_TASK;
    private boolean featuredExpanded;
    private boolean compactTasks;
    private boolean sortByDeadline;
    private TextView taskTitle;
    private ScrollView taskScroll;
    private TextView status;
    private Button listenerButton;
    private Button remindersButton;
    private TextView count;
    private LinearLayout taskList;
    private UpdateController updates;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        navigation = new BottomNavigation(this, BACKGROUND, this::selectTab, this::addManualTask);
        LinearLayout.LayoutParams navigationParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, navigation.heightWithInset(0));
        root.addView(navigation, navigationParams);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, 0);
                int height = navigation.heightWithInset(bars.bottom);
                if (navigationParams.height != height) {
                    navigationParams.height = height;
                    navigation.setLayoutParams(navigationParams);
                }
                return insets;
            });
        } else {
            getWindow().setNavigationBarColor(BottomNavigation.BAR_COLOR);
        }

        taskPage = createTaskPage();
        settingsPage = createSettingsPage();
        settingsPage.setVisibility(View.GONE);
        content.addView(taskPage);
        content.addView(settingsPage);
        setContentView(root);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        if (savedState != null) {
            int index = savedState.getInt(SELECTED_TAB);
            Tab[] tabs = Tab.values();
            if (index >= 0 && index < tabs.length) selectTab(tabs[index]);
            featuredTaskId = savedState.getLong(FEATURED_TASK, NO_FEATURED_TASK);
            featuredExpanded = savedState.getBoolean(FEATURED_EXPANDED);
            compactTasks = savedState.getBoolean(COMPACT_TASKS);
        }
    }

    private View createTaskPage() {
        LinearLayout screen = column();
        screen.setAccessibilityPaneTitle(getText(R.string.app_name));
        LinearLayout header = header(screen);
        taskTitle = label(getString(R.string.app_name), 28, INK, true);
        header.addView(taskTitle);

        taskScroll = new TaskListScrollView(this, this::collapseTaskCards);
        LinearLayout page = scrollBody(screen, taskScroll);
        count = label("", 20, INK, true);
        add(page, count, 12);
        add(page, createTaskSortControl(), 8);
        taskList = column();
        add(page, taskList, 0);
        return screen;
    }

    private View createTaskSortControl() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        sortByDeadline = preferences.getBoolean(SORT_BY_DEADLINE, true);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFFE5EDE9);
        track.setCornerRadius(dp(24));
        row.setBackground(track);

        SeekBar sort = new SeekBar(this);
        sort.setMax(1);
        sort.setProgress(sortByDeadline ? 1 : 0);
        sort.setMinimumHeight(dp(48));
        sort.setContentDescription(getText(R.string.task_sort_label));
        Button added = button(getString(R.string.task_sort_added), view -> sort.setProgress(0));
        Button deadline = button(getString(R.string.task_sort_deadline), view -> sort.setProgress(1));
        row.addView(added);
        row.addView(sort, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(deadline);

        Runnable showSelection = () -> {
            added.setTextColor(sortByDeadline ? SUBTLE : GREEN);
            deadline.setTextColor(sortByDeadline ? GREEN : SUBTLE);
            sort.setStateDescription(getText(sortByDeadline ? R.string.task_sort_deadline : R.string.task_sort_added));
        };
        showSelection.run();
        sort.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                sortByDeadline = progress == 1;
                preferences.edit().putBoolean(SORT_BY_DEADLINE, sortByDeadline).apply();
                showSelection.run();
                featuredTaskId = NO_FEATURED_TASK;
                featuredExpanded = false;
                taskScroll.scrollTo(0, 0);
                showTasks();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        return row;
    }

    private View createSettingsPage() {
        LinearLayout screen = column();
        screen.setAccessibilityPaneTitle(getText(R.string.settings_title));
        LinearLayout header = header(screen);
        TextView title = label(getString(R.string.settings_title), 24, INK, true);
        title.setAccessibilityHeading(true);
        header.addView(title);
        LinearLayout page = scrollBody(screen, new ScrollView(this));

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
        add(monitoring, label(getString(R.string.group_monitoring), 17, INK, true), 0);
        add(monitoring, label("群名和群昵称可以帮助识别群里发给你的待办。", 14, SUBTLE, false), 6);
        add(monitoring, button("管理群聊资料", view -> startActivity(
                new Intent(this, MonitoringActivity.class))), 4);

        LinearLayout clipboard = card();
        add(page, clipboard, 12);
        add(clipboard, label(getString(R.string.clipboard_title), 17, INK, true), 0);
        add(clipboard, label(getString(R.string.clipboard_help), 14, SUBTLE, false), 6);

        LinearLayout updater = card();
        add(page, updater, 12);
        add(updater, label("应用更新", 17, INK, true), 0);
        TextView updateStatus = label("", 14, SUBTLE, false);
        add(updater, updateStatus, 6);
        Button check = button("检查并下载更新", null);
        Button install = button("安装已下载的更新", null);
        add(updater, check, 4);
        add(updater, install, 0);
        updates = new UpdateController(this, updateStatus, check, install, () -> selectedTab == Tab.SETTINGS);
        add(updater, button("用浏览器下载", view -> updates.openDownloadPage()), 0);
        return screen;
    }

    private void selectTab(Tab tab) {
        if (selectedTab == tab) return;
        if (selectedTab == Tab.HOME) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, returnHome);
        } else if (tab == Tab.HOME) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(returnHome);
        }
        selectedTab = tab;
        navigation.select(tab);
        boolean settings = tab == Tab.SETTINGS;
        taskPage.setVisibility(settings ? View.GONE : View.VISIBLE);
        settingsPage.setVisibility(settings ? View.VISIBLE : View.GONE);
        if (settings) {
            clock.removeCallbacks(updateCountdowns);
            showPermissions();
            return;
        }
        int title = tab == Tab.DAILY ? R.string.daily_tasks : tab == Tab.WORK ? R.string.work_tasks : R.string.app_name;
        taskTitle.setText(title);
        taskPage.setAccessibilityPaneTitle(getText(title));
        if (tab != lastTaskTab) {
            featuredTaskId = NO_FEATURED_TASK;
            featuredExpanded = false;
            compactTasks = false;
            taskScroll.scrollTo(0, 0);
        }
        showTasks();
        lastTaskTab = tab;
    }

    private void addManualTask() {
        TaskCategory preferred = selectedTab == Tab.DAILY ? TaskCategory.DAILY
                : selectedTab == Tab.WORK ? TaskCategory.WORK : null;
        new ManualTaskDialog(this).show(preferred, this::saveManualTask);
    }

    private void saveManualTask(ManualTaskDialog.Entry entry) {
        TaskStore.get(this).addManual(entry.body(), entry.due(), entry.category());
        Reminders.scheduleNext(this);
        TaskCategory category = entry.category() == null ? TaskCategory.classify(entry.body(), TaskStore.MANUAL_SOURCE)
                : entry.category();
        Tab taskTab = category == TaskCategory.WORK ? Tab.WORK : Tab.DAILY;
        if (selectedTab != Tab.HOME && selectedTab != taskTab) selectTab(taskTab);
        else showTasks();
        Toast.makeText(this, R.string.manual_saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(SELECTED_TAB, selectedTab.ordinal());
        state.putLong(FEATURED_TASK, featuredTaskId);
        state.putBoolean(FEATURED_EXPANDED, featuredExpanded);
        state.putBoolean(COMPACT_TASKS, compactTasks);
    }

    @Override
    protected void onDestroy() {
        if (selectedTab != Tab.HOME) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(returnHome);
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
        resumed = true;
        clipboardCheckPending = true;
        sourceIcons.clear();
        if (selectedTab == Tab.SETTINGS) {
            showPermissions();
        }
        showTasks();
        Reminders.scheduleNext(this);
        updates.onResume();
        checkClipboard();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) checkClipboard();
    }

    private void checkClipboard() {
        if (!resumed || !clipboardCheckPending || !hasWindowFocus() || isFinishing()) return;
        clipboardCheckPending = false;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipDescription description = clipboard.getPrimaryClipDescription();
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        if (description == null || description.getTimestamp() <= 0
                || description.getTimestamp() == preferences.getLong(LAST_CLIPBOARD_TIMESTAMP, 0)
                || !(description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))
                || isSensitive(description)) return;

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() != 1
                || clip.getDescription().getTimestamp() != description.getTimestamp()
                || isSensitive(clip.getDescription())) return;
        preferences.edit().putLong(LAST_CLIPBOARD_TIMESTAMP, description.getTimestamp()).apply();
        CharSequence text = clip.getItemAt(0).getText();
        if (text == null || text.length() > ManualTaskDialog.MAX_BODY_LENGTH) return;
        String body = text.toString().strip();
        TaskRules.Match task = TaskRules.chatTask(body);
        if (task == null) return;

        ZonedDateTime copiedAt = Instant.ofEpochMilli(description.getTimestamp()).atZone(ZoneId.systemDefault());
        Long suggestedDeadline = TaskRules.clipboardDeadline(body, task, copiedAt, System.currentTimeMillis());
        new AlertDialog.Builder(this)
                .setTitle(R.string.clipboard_prompt)
                .setMessage(body)
                .setNegativeButton(R.string.clipboard_no, null)
                .setPositiveButton(R.string.clipboard_yes, (dialog, which) -> new ManualTaskDialog(this)
                        .confirmClipboard(body, suggestedDeadline, this::saveManualTask))
                .show();
    }

    private static boolean isSensitive(ClipDescription description) {
        var extras = description.getExtras();
        return extras != null && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
    }

    @Override
    protected void onPause() {
        resumed = false;
        clock.removeCallbacks(updateCountdowns);
        super.onPause();
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

    private void showTasks() {
        if (selectedTab == Tab.SETTINGS) return;
        List<TaskStore.Task> visible = TaskStore.get(this).list(sortByDeadline);
        if (selectedTab != Tab.HOME) {
            TaskCategory category = selectedTab == Tab.WORK ? TaskCategory.WORK : TaskCategory.DAILY;
            visible = visible.stream().filter(task -> task.category() == category).toList();
        }
        TaskStore.Task featured = visible.stream().filter(task -> task.id() == featuredTaskId).findFirst().orElse(null);
        taskList.removeAllViews();
        taskCards.clear();
        if (featured != null) {
            add(taskList, taskCard(featured, featuredExpanded ? Mode.EXPANDED : Mode.COMPACT), 12);
        } else {
            featuredTaskId = NO_FEATURED_TASK;
            featuredExpanded = false;
        }
        int open = showTaskList(visible);
        count.setText(getString(selectedTab == Tab.HOME ? R.string.open_task_count : R.string.filtered_task_count, open));
        if (selectedTab != Tab.HOME) {
            add(taskList, label("识别和保存均在手机上进行。到期提醒可能受系统省电策略延迟。",
                    13, SUBTLE, false), 14);
        }
        refreshCountdowns();
    }

    private int showTaskList(List<TaskStore.Task> tasks) {
        if (tasks.isEmpty()) {
            add(taskList, label(getString(R.string.empty_tasks), 14, SUBTLE, false), 6);
        }
        int open = 0;
        Mode mode = compactTasks ? Mode.COMPACT : Mode.STANDARD;
        for (TaskStore.Task task : tasks) {
            if (!task.done()) open++;
            if (task.id() != featuredTaskId) add(taskList, taskCard(task, mode), 10);
        }
        return open;
    }

    private TaskCardView taskCard(TaskStore.Task task, Mode mode) {
        TaskCardSource source = TaskCardSource.from(task);
        TaskCardView card = new TaskCardView(this, task, source, sourceIcon(source.app()), mode,
                () -> showTaskDetails(task, source), () -> completeTask(task), this::collapseTaskCards);
        taskCards.add(card);
        card.setOnClickListener(view -> {
            if (featuredTaskId == task.id() && featuredExpanded) collapseTaskCards();
            else expandTaskCard(task.id(), view.hasFocus());
        });
        card.setOnLongClickListener(view -> {
            new AlertDialog.Builder(this).setItems(new String[]{"编辑", "删除"}, (dialog, option) -> {
                if (option == 0) editTask(task);
                else confirmDelete(task);
            }).show();
            return true;
        });
        return card;
    }

    private void expandTaskCard(long id, boolean restoreFocus) {
        featuredTaskId = id;
        featuredExpanded = true;
        compactTasks = true;
        taskScroll.scrollTo(0, 0);
        showTasks();
        if (featuredTaskId != id) return;
        TaskCardView expanded = taskCards.get(0);
        if (restoreFocus) expanded.requestFocus();
        expanded.setScaleX(0.97f);
        expanded.setScaleY(0.97f);
        expanded.animate().scaleX(1).scaleY(1).setDuration(180).start();
    }

    private void collapseTaskCards() {
        if (compactTasks && !featuredExpanded) return;
        featuredExpanded = false;
        compactTasks = true;
        for (TaskCardView card : taskCards) card.setMode(Mode.COMPACT);
        refreshCountdowns();
    }

    private void refreshCountdowns() {
        clock.removeCallbacks(updateCountdowns);
        if (!resumed || selectedTab == Tab.SETTINGS) return;
        long now = System.currentTimeMillis();
        long delay = Long.MAX_VALUE;
        for (TaskCardView card : taskCards) delay = Math.min(delay, card.refreshTime(now));
        if (delay != Long.MAX_VALUE) clock.postDelayed(updateCountdowns, delay);
    }

    private Drawable sourceIcon(App app) {
        if (app == null) return null;
        if (!sourceIcons.containsKey(app)) {
            try {
                sourceIcons.put(app, getPackageManager().getApplicationIcon(app.packageName).getConstantState());
            } catch (PackageManager.NameNotFoundException unavailable) {
                sourceIcons.put(app, null);
            }
        }
        Drawable.ConstantState icon = sourceIcons.get(app);
        return icon == null ? null : icon.newDrawable(getResources()).mutate();
    }

    private void showTaskDetails(TaskStore.Task task, TaskCardSource source) {
        String due = task.due() == null ? "无截止时间" : "截止 " + TaskTime.full(task.due(), ZoneId.systemDefault());
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(source.title())
                .setMessage(task.body() + "\n\n" + due + (task.done() ? "\n已完成" : ""))
                .setNegativeButton("关闭", null);
        if (!task.done()) dialog.setPositiveButton("完成", (view, action) -> completeTask(task));
        dialog.show();
    }

    private void completeTask(TaskStore.Task task) {
        TaskStore.get(this).complete(task.id());
        afterTaskChanged(task.id());
    }

    private void editTask(TaskStore.Task task) {
        new ManualTaskDialog(this).edit(task, entry -> {
            if (TaskStore.get(this).edit(task.id(), entry.body(), entry.due())) {
                afterTaskChanged(task.id());
                Toast.makeText(this, R.string.edit_task_saved, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDelete(TaskStore.Task task) {
        new AlertDialog.Builder(this)
                .setTitle("删除这项待办？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    TaskStore.get(this).delete(task.id());
                    afterTaskChanged(task.id());
                })
                .show();
    }

    private void afterTaskChanged(long id) {
        Reminders.dismiss(this, id);
        Reminders.scheduleNext(this);
        showTasks();
    }

    private LinearLayout header(LinearLayout screen) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(8));
        add(screen, header, 0);
        return header;
    }

    private LinearLayout scrollBody(LinearLayout screen, ScrollView scroll) {
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
        return card(16);
    }

    private LinearLayout card(int horizontalPadding) {
        LinearLayout layout = column();
        int side = dp(horizontalPadding);
        layout.setPadding(side, dp(13), side, dp(13));
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
