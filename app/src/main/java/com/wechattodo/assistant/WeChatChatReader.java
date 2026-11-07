package com.wechattodo.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.time.ZonedDateTime;

public final class WeChatChatReader extends AccessibilityService {
    private static final String PREFERENCES = "chat_reader";
    private static final String AUTO_READ = "auto_read";
    private static final long SCAN_DELAY = 350;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scan = this::readVisibleChat;
    private int eventWindowId = -1;
    private VisibleWeChatChat lastScan;

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
        if (WeChatListener.WECHAT.equals(String.valueOf(event.getPackageName()))) {
            eventWindowId = event.getWindowId();
            scheduleScan();
        }
    }

    private void scheduleScan() {
        if (!isEnabled(this)) {
            lastScan = null;
        } else if (!handler.hasCallbacks(scan)) {
            handler.postDelayed(scan, SCAN_DELAY);
        }
    }

    private void readVisibleChat() {
        if (!isEnabled(this)) {
            lastScan = null;
            return;
        }
        VisibleWeChatChat chat = VisibleWeChatChat.read(activeWeChatRoot());
        if (chat == null || chat.equals(lastScan)) {
            return;
        }
        lastScan = chat;
        if (TaskStore.get(this).addFromChat(chat, ZonedDateTime.now())) {
            Reminders.scheduleNext(this);
            TaskStore.signalChange(this);
        }
    }

    private AccessibilityNodeInfo activeWeChatRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow(0);
        if (isWeChat(active)) {
            return active;
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
        handler.removeCallbacks(scan);
        eventWindowId = -1;
        lastScan = null;
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
