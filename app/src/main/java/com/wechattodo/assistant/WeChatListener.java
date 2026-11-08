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

import com.wechattodo.assistant.MonitoringRules.App;
import com.wechattodo.assistant.MonitoringRules.Channel;

public final class WeChatListener extends NotificationListenerService {
    static final String WECHAT = App.WECHAT.packageName;
    private static final long RECENT_MESSAGE = 5 * 60_000L;

    @Override
    public void onNotificationPosted(StatusBarNotification posted) {
        App app = posted == null ? null : App.fromPackage(posted.getPackageName());
        if (app == null) {
            return;
        }
        MonitoringSettings settings = new MonitoringSettings(this);
        Notification notification = posted.getNotification();
        Bundle extras = notification.extras;
        if (extras == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return;
        }
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE), 120);
        List<Message> messages = Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle.class));
        String conversation = text(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE), 120);
        Boolean group = groupEvidence(extras, messages, conversation, title);
        Channel channel = MonitoringRules.notification(settings.conversations(app), conversation, title, group);
        boolean changed = false;
        if (!messages.isEmpty()) {
            for (Message message : messages) {
                changed |= captureMessage(app, channel, message, posted.getPostTime());
            }
        } else {
            String body = text(extras.getCharSequence(Notification.EXTRA_TEXT), 2000);
            if (body.isEmpty()) {
                body = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), 2000);
            }
            String sender = channel.kind() == RecipientRules.Context.PRIVATE && !channel.title().isEmpty()
                    ? channel.title() : null;
            changed = capture(app, channel, channel.title(), sender, body, posted.getPostTime(), 0);
        }
        if (changed) {
            Reminders.scheduleNext(this);
            TaskStore.signalChange(this);
        }
    }

    private static Boolean groupEvidence(Bundle extras, List<Message> messages, String conversation, String title) {
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return true;
        String expected = MonitoringRules.normalize(conversation.isEmpty() ? title : conversation);
        String firstSender = "";
        for (Message message : messages) {
            Person person = message.getSenderPerson();
            String sender = person == null ? "" : MonitoringRules.normalize(text(person.getName(), 120));
            if (sender.isEmpty()) continue;
            if ((!conversation.isEmpty() && !expected.equals(sender))
                    || (!firstSender.isEmpty() && !firstSender.equals(sender))) return true;
            firstSender = sender;
        }
        return !firstSender.isEmpty() && expected.equals(firstSender)
                && extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION) ? Boolean.FALSE : null;
    }

    private boolean captureMessage(App app, Channel channel, Message message, long postTime) {
        long timestamp = message.getTimestamp();
        if (timestamp > 0 && timestamp < postTime - RECENT_MESSAGE) {
            return false;
        }
        Person person = message.getSenderPerson();
        String sender = person == null ? "" : text(person.getName(), 120);
        if (sender.isEmpty()) {
            return false;
        }
        String source = channel.title().isEmpty() || channel.title().equals(sender)
                ? sender : channel.title() + " · " + sender;
        String body = text(message.getText(), 2000);
        long received = timestamp > 0 && timestamp <= postTime ? timestamp : postTime;
        return capture(app, channel, source, sender, body, received, timestamp);
    }

    private boolean capture(App app, Channel channel, String source, String sender, String body,
                            long received, long messageTime) {
        RecipientRules.Decision decision = channel.route(body);
        if (decision.action() != RecipientRules.Action.AUTO) return false;
        TaskRules.Match task = TaskRules.notificationTask(decision.text());
        if (task == null) {
            return false;
        }
        Long due = task.dueAt(Instant.ofEpochMilli(received).atZone(ZoneId.systemDefault()));
        String label = source.isEmpty() ? app.label : app == App.WECHAT ? source : app.label + " · " + source;
        return TaskStore.get(this).add(label, body, decision.text(), due, messageTime,
                new TaskStore.Origin(app.packageName, sender)) != null;
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
