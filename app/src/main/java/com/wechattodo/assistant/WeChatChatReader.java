package com.wechattodo.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZonedDateTime;

public final class WeChatChatReader extends AccessibilityService {
    private static final String PREFERENCES = "chat_reader";
    private static final String AUTO_READ = "auto_read";
    private static final long SCAN_DELAY = 350;
    private static final int FOREGROUND_CHANGED = AccessibilityEvent.WINDOWS_CHANGE_ACTIVE
            | AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scan = this::readVisibleChat;
    private int eventWindowId = -1;
    private VisibleWeChatChat lastScan;
    private Candidate candidate;
    private LinearLayout prompt;

    private record Candidate(String chat, String body, String original) {
        boolean isVisibleIn(VisibleWeChatChat page) {
            return page != null && chat.equals(page.title()) && page.messages().contains(body)
                    && original.equals(page.original(body));
        }
    }

    static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(AUTO_READ, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(AUTO_READ, enabled).apply();
    }

    static boolean hasAccess(Context context) {
        ComponentName component = new ComponentName(context, WeChatChatReader.class);
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream()
                .anyMatch(service -> component.equals(ComponentName.unflattenFromString(service.getId())));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
    }

    @Override
    protected void onServiceConnected() {
        scheduleScan();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        String app = String.valueOf(event.getPackageName());
        boolean focusChanged = type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && (event.getWindowChanges() & FOREGROUND_CHANGED) != 0;
        boolean otherWindowOpened = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && candidate != null && !getPackageName().equals(app);
        if (WeChatListener.WECHAT.equals(app) && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            eventWindowId = event.getWindowId();
            scheduleScan();
        } else if (focusChanged || otherWindowOpened) {
            if (candidate != null && activeWeChatRoot() == null) {
                clearChat();
            } else {
                scheduleScan();
            }
        }
    }

    private void scheduleScan() {
        if (!isEnabled(this)) {
            clearChat();
        } else if (!handler.hasCallbacks(scan)) {
            handler.postDelayed(scan, SCAN_DELAY);
        }
    }

    private void readVisibleChat() {
        if (!isEnabled(this)) {
            clearChat();
            return;
        }
        VisibleWeChatChat chat = VisibleWeChatChat.read(activeWeChatRoot(), new MonitoringSettings(this));
        if (chat == null) {
            clearChat();
            return;
        }
        if (chat.equals(lastScan)) {
            return;
        }
        lastScan = chat;
        if (candidate != null && candidate.isVisibleIn(chat)) {
            return;
        }
        closePrompt();
        String body = TaskStore.get(this).nextChatCandidate(chat);
        if (body != null) {
            showPrompt(new Candidate(chat.title(), body, chat.original(body)));
        }
    }

    private void showPrompt(Candidate proposed) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFAFDFC);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xFFB5CBC3);
        card.setBackground(background);
        card.setElevation(dp(6));

        TextView heading = new TextView(this);
        heading.setText(R.string.chat_candidate_title);
        heading.setTextColor(0xFF17332B);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextSize(16);
        card.addView(heading);

        TextView preview = new TextView(this);
        preview.setText(proposed.body());
        preview.setTextColor(0xFF17332B);
        preview.setTextSize(14);
        preview.setMaxLines(3);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(preview);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        Button skip = promptButton(R.string.chat_candidate_skip, 0xFF475D55);
        skip.setOnClickListener(view -> resolvePrompt(proposed, false));
        buttons.addView(skip);
        Button add = promptButton(R.string.chat_candidate_add, 0xFF116654);
        add.setOnClickListener(view -> resolvePrompt(proposed, true));
        buttons.addView(add);
        card.addView(buttons);

        int available = getResources().getDisplayMetrics().widthPixels - dp(32);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.min(available, dp(360)), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(74);
        try {
            getSystemService(WindowManager.class).addView(card, params);
            prompt = card;
            candidate = proposed;
        } catch (WindowManager.BadTokenException unavailable) {
            lastScan = null;
        }
    }

    private Button promptButton(int label, int color) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setTextColor(color);
        button.setMinHeight(dp(48));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void resolvePrompt(Candidate selected, boolean accepted) {
        if (selected != candidate) {
            return;
        }
        VisibleWeChatChat current = isEnabled(this)
                ? VisibleWeChatChat.read(activeWeChatRoot(), new MonitoringSettings(this)) : null;
        closePrompt();
        lastScan = null;
        if (selected.isVisibleIn(current) && TaskStore.get(this).resolveChatCandidate(
                selected.chat(), selected.body(), accepted, ZonedDateTime.now(), current.sender(selected.body()))) {
            Reminders.scheduleNext(this);
            TaskStore.signalChange(this);
            Toast.makeText(this, R.string.chat_candidate_saved, Toast.LENGTH_SHORT).show();
        }
        scheduleScan();
    }

    private AccessibilityNodeInfo activeWeChatRoot() {
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            return null;
        }
        AccessibilityNodeInfo active = getRootInActiveWindow(0);
        if (isWeChat(active)) {
            eventWindowId = active.getWindowId();
            return active;
        }
        if (active != null && !getPackageName().equals(String.valueOf(active.getPackageName()))) {
            return null;
        }
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getId() == eventWindowId && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                    && window.isFocused()) {
                AccessibilityNodeInfo root = window.getRoot(0);
                return isWeChat(root) ? root : null;
            }
        }
        return null;
    }

    private static boolean isWeChat(AccessibilityNodeInfo node) {
        return node != null && WeChatListener.WECHAT.equals(String.valueOf(node.getPackageName()));
    }

    @Override
    public void onInterrupt() {
        clearChat();
    }

    private void clearChat() {
        handler.removeCallbacks(scan);
        eventWindowId = -1;
        lastScan = null;
        closePrompt();
    }

    private void closePrompt() {
        candidate = null;
        if (prompt != null) {
            getSystemService(WindowManager.class).removeViewImmediate(prompt);
            prompt = null;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        onInterrupt();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        onInterrupt();
        super.onDestroy();
    }
}
