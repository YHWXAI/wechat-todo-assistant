package com.wechattodo.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.time.ZonedDateTime;
import java.util.List;

public final class WeChatChatReader extends AccessibilityService {
    private static WeakReference<WeChatChatReader> connected = new WeakReference<>(null);
    private static final long ARM_TIME = 90_000;
    private static final long PREVIEW_TIME = 120_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::disarm;
    private final Runnable clearPreview = () -> preview = null;
    private boolean armed;
    private Button overlay;
    private Preview preview;

    record Preview(String chat, List<String> messages, ZonedDateTime scannedAt) {}

    static boolean armReader() {
        WeChatChatReader reader = connected.get();
        if (reader == null) {
            return false;
        }
        reader.arm();
        return true;
    }

    static Preview takePreview() {
        WeChatChatReader reader = connected.get();
        if (reader == null) {
            return null;
        }
        Preview result = reader.preview;
        reader.clearPreview.run();
        return result;
    }

    static boolean isReady() {
        return connected.get() != null;
    }

    @Override
    protected void onServiceConnected() {
        connected = new WeakReference<>(this);
    }

    private void arm() {
        disarm();
        clearPreview.run();
        armed = true;
        handler.postDelayed(expire, ARM_TIME);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!armed || event.getPackageName() == null) {
            return;
        }
        String app = event.getPackageName().toString();
        if (WeChatListener.WECHAT.equals(app)) {
            showOverlay();
        } else if (!getPackageName().equals(app)
                || MainActivity.class.getName().equals(String.valueOf(event.getClassName()))) {
            hideOverlay();
        }
    }

    private void showOverlay() {
        if (overlay != null) {
            return;
        }
        Button button = new Button(this);
        button.setText("读取当前聊天");
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFF116654));
        button.setOnClickListener(view -> {
            disarm();
            handler.postDelayed(this::readVisibleChat, 120);
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        float density = getResources().getDisplayMetrics().density;
        params.x = Math.round(12 * density);
        params.y = Math.round(76 * density);
        try {
            getSystemService(WindowManager.class).addView(button, params);
            overlay = button;
        } catch (WindowManager.BadTokenException ignored) {
            disarm();
        }
    }

    private void readVisibleChat() {
        if (connected.get() != this) {
            return;
        }
        VisibleWeChatChat chat = VisibleWeChatChat.read(getRootInActiveWindow(0));
        if (chat == null) {
            toast("未识别到当前微信聊天。请打开聊天并切到文字输入模式后重试。");
        } else if (chat.messages().isEmpty()) {
            toast("当前页面没有识别到待办文字。");
        } else {
            preview = new Preview(chat.title(), chat.messages(), ZonedDateTime.now());
            handler.removeCallbacks(clearPreview);
            handler.postDelayed(clearPreview, PREVIEW_TIME);
            toast("已找到候选待办，请回到微信待办助手勾选保存。");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void hideOverlay() {
        if (overlay != null) {
            getSystemService(WindowManager.class).removeViewImmediate(overlay);
            overlay = null;
        }
    }

    private void disarm() {
        armed = false;
        handler.removeCallbacks(expire);
        hideOverlay();
    }

    @Override
    public void onInterrupt() {
        disarm();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        closeReader();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        closeReader();
        super.onDestroy();
    }

    private void closeReader() {
        disarm();
        clearPreview.run();
        handler.removeCallbacksAndMessages(null);
        if (connected.get() == this) {
            connected.clear();
        }
    }
}
