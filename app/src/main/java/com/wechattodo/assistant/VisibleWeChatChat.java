package com.wechattodo.assistant;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

record VisibleWeChatChat(String title, List<String> messages) {
    private static final int MAX_NODES = 450;
    private static final int MAX_MESSAGES = 20;

    private record Title(String text, int bottom) {}
    private record Composer(int top, boolean hasChatControls) {}

    static VisibleWeChatChat read(AccessibilityNodeInfo root) {
        if (root == null || !WeChatListener.WECHAT.equals(String.valueOf(root.getPackageName()))) {
            return null;
        }
        Rect screen = bounds(root);
        if (screen.isEmpty()) {
            return null;
        }
        List<AccessibilityNodeInfo> nodes = visibleNodes(root);
        Composer composer = composer(nodes, screen);
        if (composer == null) {
            return null;
        }
        AccessibilityNodeInfo chatList = messageList(nodes, screen, composer.top());
        int titleLimit = screen.top + screen.height() / 6;
        if (chatList != null && bounds(chatList).top > screen.top + screen.height() / 12) {
            titleLimit = Math.min(titleLimit, bounds(chatList).top);
        }
        Title title = chatTitle(nodes, screen, titleLimit);
        if (title == null) {
            return null;
        }
        Rect messageArea = new Rect(screen.left, title.bottom(), screen.right, composer.top());
        if (chatList != null && !messageArea.intersect(bounds(chatList))) {
            return null;
        }
        LinkedHashSet<String> messages = new LinkedHashSet<>();
        List<AccessibilityNodeInfo> candidates = chatList == null ? nodes : visibleNodes(chatList);
        for (AccessibilityNodeInfo node : candidates) {
            Rect area = bounds(node);
            if (node.isEditable() || node.isPassword() || !messageArea.contains(area)
                    || area.isEmpty() || area.height() > screen.height() / 3) {
                continue;
            }
            String text = WeChatListener.text(node.getText(), 2000);
            if (text.isEmpty() && node.getChildCount() == 0) {
                text = WeChatListener.text(node.getContentDescription(), 2000);
            }
            if (TaskRules.isChatTask(text)
                    && isIncoming(nodes, area, screen, chatList == null && !composer.hasChatControls())) {
                messages.add(text);
                if (messages.size() == MAX_MESSAGES) {
                    break;
                }
            }
        }
        return new VisibleWeChatChat(title.text(), List.copyOf(messages));
    }

    private static Composer composer(List<AccessibilityNodeInfo> nodes, Rect screen) {
        int editor = -1;
        int emoji = -1;
        int sideButton = -1;
        for (AccessibilityNodeInfo node : nodes) {
            Rect area = bounds(node);
            if (area.top < screen.centerY() || area.isEmpty() || node.isPassword()) {
                continue;
            }
            String role = String.valueOf(node.getClassName());
            if (node.isEditable() || role.endsWith("EditText") || node.getInputType() != 0) {
                editor = Math.max(editor, area.top);
            }
            if (area.width() > screen.width() / 3) {
                continue;
            }
            String description = WeChatListener.text(node.getContentDescription(), 100);
            if (description.contains("表情") || description.equalsIgnoreCase("emoji")) {
                emoji = Math.max(emoji, area.top);
            } else if (description.contains("更多功能") || description.contains("切换到")
                    && (description.contains("说话") || description.contains("键盘") || description.contains("语音"))
                    || description.equals("语音输入") || description.equalsIgnoreCase("More functions")) {
                sideButton = Math.max(sideButton, area.top);
            }
        }
        boolean controls = emoji >= 0 && sideButton >= 0
                && Math.abs(emoji - sideButton) <= screen.height() / 20;
        if (editor >= 0) {
            return new Composer(editor, controls && Math.abs(editor - emoji) <= screen.height() / 15);
        }
        return controls ? new Composer(Math.min(emoji, sideButton), true) : null;
    }

    private static AccessibilityNodeInfo messageList(
            List<AccessibilityNodeInfo> nodes, Rect screen, int editorTop) {
        AccessibilityNodeInfo list = null;
        int largest = screen.height() / 5;
        for (AccessibilityNodeInfo node : nodes) {
            String role = String.valueOf(node.getClassName());
            if (!node.isScrollable() && node.getCollectionInfo() == null
                    && !role.endsWith("RecyclerView") && !role.endsWith("ListView")) {
                continue;
            }
            Rect area = bounds(node);
            if (area.height() > largest && area.width() >= screen.width() / 2
                    && area.top < screen.centerY() && area.bottom <= editorTop + screen.height() / 20
                    && area.bottom >= editorTop - screen.height() / 4) {
                list = node;
                largest = area.height();
            }
        }
        return list;
    }

    private static Title chatTitle(List<AccessibilityNodeInfo> nodes, Rect screen, int limit) {
        Title title = null;
        int closest = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo node : nodes) {
            Rect area = bounds(node);
            if (node.isEditable() || node.isPassword() || area.isEmpty() || area.bottom > limit
                    || area.top < screen.top || Math.abs(area.centerX() - screen.centerX()) > screen.width() / 5) {
                continue;
            }
            String text = WeChatListener.text(node.getText(), 120);
            int score = Math.abs(area.centerX() - screen.centerX()) + limit - area.bottom;
            if (!text.isEmpty() && !text.equals("微信") && !text.equals("WeChat") && !text.equals("搜索")
                    && !text.matches("[\\d:：\\s]+") && score < closest) {
                title = new Title(text, area.bottom);
                closest = score;
            }
        }
        return title;
    }

    private static boolean isIncoming(
            List<AccessibilityNodeInfo> nodes, Rect message, Rect screen, boolean requireAvatar) {
        int width = screen.width();
        int leftInset = message.left - screen.left;
        int rightInset = screen.right - message.right;
        if (leftInset < width / 14 || leftInset > width / 3 || rightInset < width / 14) {
            return false;
        }

        for (AccessibilityNodeInfo node : nodes) {
            String role = String.valueOf(node.getClassName());
            if (!role.endsWith("ImageView") && !String.valueOf(node.getContentDescription()).endsWith("头像")) {
                continue;
            }
            Rect avatar = bounds(node);
            if (avatar.isEmpty() || avatar.width() > width / 6
                    || Math.abs(avatar.top - message.top) > width / 20) {
                continue;
            }
            if (avatar.left >= screen.right - width / 6 && avatar.left >= message.right) {
                return false;
            }
            if (avatar.right <= screen.left + width / 6 && avatar.right <= message.left) {
                return true;
            }
        }
        return !requireAvatar && leftInset <= width / 4 && leftInset <= rightInset + width / 25;
    }

    private static List<AccessibilityNodeInfo> visibleNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> remaining = new ArrayDeque<>();
        remaining.add(root);
        int visited = 0;
        while (!remaining.isEmpty() && visited < MAX_NODES) {
            AccessibilityNodeInfo node = remaining.removeFirst();
            visited++;
            if (!WeChatListener.WECHAT.equals(String.valueOf(node.getPackageName()))) {
                continue;
            }
            if (node.isVisibleToUser()) {
                result.add(node);
            }
            for (int i = 0; i < node.getChildCount() && remaining.size() + visited < MAX_NODES; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    remaining.addLast(child);
                }
            }
        }
        return result;
    }

    private static Rect bounds(AccessibilityNodeInfo node) {
        Rect area = new Rect();
        node.getBoundsInScreen(area);
        return area;
    }
}
