package com.wechattodo.assistant;

import android.view.accessibility.AccessibilityNodeInfo;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public final class ChatLayoutCheck {
    private static final String TASK = "中午你买点东西回来";
    private static final String TASK_TWO = "回来的时候带点菜不？";

    public static void main(String[] args) {
        for (int[] size : new int[][]{{1000, 2000}, {1440, 3120}, {1000, 1200}}) {
            checkLayout("FrameLayout", false, true, size[0], size[1]);
            checkLayout("FrameLayout", true, false, size[0], size[1]);
            checkLayout("ListView", true, false, size[0], size[1]);
        }
        AccessibilityNodeInfo search = node("FrameLayout", null, 0, 0, 1000, 2000);
        search.children.add(node("EditText", "搜索", 60, 80, 940, 150));
        check(VisibleWeChatChat.read(search) == null, "search screen");
        search.app = "other.app";
        check(VisibleWeChatChat.read(search) == null, "other app");
        check(!TaskRules.isChatTask("你买了吗？"), "status question");
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 7, 11, 0, 0, 0, ZoneId.of("Asia/Shanghai"));
        check(TaskRules.dueFromChat("请明天寄样品", now) == null, "ambiguous relative date");
        check(TaskRules.dueFromChat("请2026年11月9日15:30前发文件", now) != null, "absolute future deadline");
        check(TaskRules.dueFromChat("请2026年11月6日发文件", now) == null, "old deadline");
        System.out.println("Chat layouts, incoming filtering, and date checks passed.");
    }

    private static void checkLayout(String listRole, boolean editor, boolean controls, int width, int height) {
        AccessibilityNodeInfo root = node("FrameLayout", null, 0, 0, 1000, 2000);
        root.visible = false;
        root.children.add(node("TextView", "测试聊天", 400, 92, 600, 152));
        AccessibilityNodeInfo list = node(listRole, null, 0, 165, 1000, 1830);
        root.children.add(list);
        list.children.add(node("ImageView", null, 26, 490, 125, 589));
        list.children.add(node("TextView", TASK, 165, 502, 525, 568));
        list.children.add(node("ImageView", null, 26, 850, 125, 949));
        AccessibilityNodeInfo bubble = node("TextView", null, 170, 860, 740, 955);
        bubble.description = TASK_TWO;
        list.children.add(bubble);
        list.children.add(node("ImageView", null, 875, 1100, 973, 1198));
        list.children.add(node("TextView", "请明天发送测试文件", 170, 1112, 830, 1170));
        AccessibilityNodeInfo password = node("TextView", "请明天发出", 160, 1390, 540, 1470);
        password.password = true;
        list.children.add(password);
        if (editor) {
            root.children.add(node("EditText", "请明天发未发送草稿", 140, 1850, 760, 1935));
        }
        if (controls) {
            AccessibilityNodeInfo emoji = node("ImageButton", null, 800, 1850, 880, 1930);
            emoji.description = "表情";
            root.children.add(emoji);
            AccessibilityNodeInfo more = node("ImageButton", null, 900, 1850, 980, 1930);
            more.description = "更多功能";
            root.children.add(more);
        }
        resize(root, width, height);
        VisibleWeChatChat chat = VisibleWeChatChat.read(root);
        check(chat != null && "测试聊天".equals(chat.title())
                && chat.messages().equals(List.of(TASK, TASK_TWO)), listRole + " layout with editor=" + editor);
    }

    private static void resize(AccessibilityNodeInfo node, int width, int height) {
        node.rect.left = node.rect.left * width / 1000;
        node.rect.right = node.rect.right * width / 1000;
        node.rect.top = node.rect.top * height / 2000;
        node.rect.bottom = node.rect.bottom * height / 2000;
        for (AccessibilityNodeInfo child : node.children) {
            resize(child, width, height);
        }
    }

    private static AccessibilityNodeInfo node(String role, String text, int x1, int y1, int x2, int y2) {
        return new AccessibilityNodeInfo(role, text, x1, y1, x2, y2);
    }

    private static void check(boolean value, String test) {
        if (!value) throw new AssertionError(test);
    }
}
