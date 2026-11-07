package com.wechattodo.assistant;

import android.app.Notification;
import android.app.Notification.MessagingStyle.Message;
import android.app.Person;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public final class WeChatListener extends NotificationListenerService {
    static final String WECHAT = "com.tencent.mm";
    private static final long RECENT_MESSAGE = 5 * 60_000L;

    @Override
    public void onNotificationPosted(StatusBarNotification posted) {
        if (posted == null || !WECHAT.equals(posted.getPackageName())) {
            return;
        }
        Notification notification = posted.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE), 120);
        List<Message> messages = Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle.class));
        boolean changed = false;
        if (!messages.isEmpty()) {
            String conversation = text(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE), 120);
            for (Message message : messages) {
                changed |= captureMessage(message, title, conversation, posted.getPostTime());
            }
        } else if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
            String body = text(extras.getCharSequence(Notification.EXTRA_TEXT), 2000);
            if (body.isEmpty()) {
                body = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), 2000);
            }
            changed = capture(title, body, posted.getPostTime(), 0);
        }
        if (changed) {
            Reminders.scheduleNext(this);
            TaskStore.signalChange(this);
        }
    }

    private boolean captureMessage(Message message, String title, String conversation, long postTime) {
        long timestamp = message.getTimestamp();
        if (timestamp > 0 && timestamp < postTime - RECENT_MESSAGE) {
            return false;
        }
        Person person = message.getSenderPerson();
        String sender = person == null ? "" : text(person.getName(), 120);
        if (sender.isEmpty()) {
            return false;
        }
        String source = conversation.isEmpty() || conversation.equals(sender)
                ? sender : conversation + " · " + sender;
        if (conversation.isEmpty() && !title.isEmpty() && !title.equals(sender)) {
            source = title + " · " + sender;
        }
        String body = text(message.getText(), 2000);
        long received = timestamp > 0 && timestamp <= postTime ? timestamp : postTime;
        return capture(source, body, received, timestamp);
    }

    private boolean capture(String source, String body, long received, long messageTime) {
        TaskRules.Match task = TaskRules.notificationTask(body);
        if (task == null) {
            return false;
        }
        Long due = task.dueAt(Instant.ofEpochMilli(received).atZone(ZoneId.systemDefault()));
        return TaskStore.get(this).add(source.isEmpty() ? "微信" : source, body, due, messageTime) != null;
    }

    static String text(CharSequence value, int limit) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        if (text.length() <= limit) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.substring(0, end).trim();
    }
}
