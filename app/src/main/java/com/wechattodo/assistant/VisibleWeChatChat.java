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

    static VisibleWeChatChat read(AccessibilityNodeInfo root) {
        if (root == null || !WeChatListener.WECHAT.equals(String.valueOf(root.getPackageName()))) {
            return null;
        }
        Rect screen = bounds(root);
        if (screen.isEmpty()) {
            return null;
        }
        List<AccessibilityNodeInfo> nodes = visibleNodes(root);
        int editorTop = editorTop(nodes, screen);
        AccessibilityNodeInfo chatList = messageList(nodes, screen, editorTop);
        if (chatList == null) {
            return null;
        }
        Rect listBounds = bounds(chatList);
        String title = chatTitle(nodes, screen, listBounds.top);
        LinkedHashSet<String> messages = new LinkedHashSet<>();
        for (AccessibilityNodeInfo node : visibleNodes(chatList)) {
            if (node.isEditable() || node.isPassword() || !Rect.intersects(bounds(node), listBounds)) {
                continue;
            }
            String text = WeChatListener.text(node.getText(), 2000);
            if (TaskRules.isTask(text)) {
                messages.add(text);
                if (messages.size() == MAX_MESSAGES) {
                    break;
                }
            }
        }
        return new VisibleWeChatChat(title, List.copyOf(messages));
    }

    private static int editorTop(List<AccessibilityNodeInfo> nodes, Rect screen) {
        int top = -1;
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isEditable() && !node.isPassword()) {
                Rect area = bounds(node);
                if (area.top >= screen.centerY()) {
                    top = Math.max(top, area.top);
                }
            }
        }
        return top;
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
                    && area.bottom >= editorTop - screen.height() / 4 && editorTop >= 0) {
                list = node;
                largest = area.height();
            }
        }
        return list;
    }

    private static String chatTitle(List<AccessibilityNodeInfo> nodes, Rect screen, int listTop) {
        String title = "当前微信聊天";
        int closest = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo node : nodes) {
            Rect area = bounds(node);
            if (node.isEditable() || node.isPassword() || area.bottom > listTop
                    || area.top < screen.top || area.width() > screen.width() * 3 / 4) {
                continue;
            }
            String text = WeChatListener.text(node.getText(), 120);
            int score = Math.abs(area.centerX() - screen.centerX()) + listTop - area.bottom;
            if (!text.isEmpty() && !text.equals("微信") && score < closest) {
                title = text;
                closest = score;
            }
        }
        return title;
    }

    private static List<AccessibilityNodeInfo> visibleNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> remaining = new ArrayDeque<>();
        remaining.add(root);
        while (!remaining.isEmpty() && result.size() < MAX_NODES) {
            AccessibilityNodeInfo node = remaining.removeFirst();
            if (!node.isVisibleToUser()
                    || !WeChatListener.WECHAT.equals(String.valueOf(node.getPackageName()))) {
                continue;
            }
            result.add(node);
            for (int i = 0; i < node.getChildCount() && remaining.size() + result.size() < MAX_NODES; i++) {
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
