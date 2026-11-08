package com.wechattodo.assistant;

import android.app.Notification;
import android.app.Notification.MessagingStyle.Message;
import android.app.Person;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wechattodo.assistant.MonitoringRules.App;
import com.wechattodo.assistant.MonitoringRules.Channel;

public final class WeChatListener extends NotificationListenerService {
    static final String WECHAT = App.WECHAT.packageName;
    private static final long RECENT_MESSAGE = 5 * 60_000L;
    private static final Pattern WECHAT_COUNT_PREFIX = Pattern.compile(
            "^[\\[［][0-9０-９]{1,5}\\s*条(?:消息)?[\\]］]\\s*([^:：\\r\\n]{1,120})[:：]\\s*");
    private static final Pattern DINGTALK_COUNT_PREFIX = Pattern.compile(
            "^[\\[［][0-9０-９]{1,5}\\s*条(?:消息)?[\\]］]\\s*");
    private static final Pattern DINGTALK_SENDER_PREFIX = Pattern.compile("^([^:：\\r\\n]{1,120})[:：]\\s*");
    private static final Pattern DINGTALK_AFFILIATION = Pattern.compile(
            "\\([^()群]{0,45}(?:公司|部门|学校|年级|班级|家长|组织|员工)[^()群]{0,30}\\)");
    private static final Pattern LEADING_QUESTION = Pattern.compile("^[^。！!？?；;\\r\\n]+[？?]\\s*");

    @Override
    public void onNotificationPosted(StatusBarNotification posted) {
        App app = posted == null ? null : App.fromPackage(posted.getPackageName());
        if (app == null) {
            return;
        }
        MonitoringSettings settings = new MonitoringSettings(this);
        Notification notification = posted.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }
        boolean summary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE), 120);
        if (app == App.DINGTALK && isDingTalkAppTitle(title)) {
            title = text(extras.getCharSequence(Notification.EXTRA_TITLE_BIG), 120);
            if (isDingTalkAppTitle(title)) title = "";
        }
        List<Message> messages = Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle.class));
        String conversation = text(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE), 120);
        var configured = settings.conversations(app);
        boolean affiliation = app == App.DINGTALK && hasAffiliation(conversation, title)
                && MonitoringRules.notification(configured, conversation, title, null).kind() == RecipientRules.Context.PRIVATE;
        Boolean group = groupEvidence(app, extras, messages, conversation, title, affiliation);
        boolean dingTalkPrivate = app == App.DINGTALK && Boolean.FALSE.equals(group);
        Channel channel = MonitoringRules.notification(configured,
                dingTalkPrivate && !title.isEmpty() ? "" : conversation, title, group);
        boolean singleContactSummary = summary && !messages.isEmpty() && Boolean.FALSE.equals(group)
                && allFromContact(messages, channel.title())
                && (dingTalkPrivate || extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION));
        boolean changed = false;
        if (summary && !singleContactSummary) {
            if ((app == App.WECHAT || app == App.DINGTALK) && channel.kind() == RecipientRules.Context.PRIVATE) {
                changed = captureExpandedText(app, channel, extras, posted.getPostTime(), true,
                        dingTalkPrivate && !title.isEmpty());
            }
        } else if (!messages.isEmpty()) {
            for (Message message : messages) {
                changed |= captureMessage(app, channel, message, posted.getPostTime());
            }
            if (!summary && channel.kind() == RecipientRules.Context.PRIVATE
                    && allFromContact(messages, channel.title())
                    && messages.stream().allMatch(message -> isStale(message, posted.getPostTime()))) {
                changed |= captureFreshPreview(app, channel, extras, messages, posted.getPostTime());
            }
        } else if (channel.kind() == RecipientRules.Context.PRIVATE
                && (app == App.WECHAT || app == App.DINGTALK
                || (!channel.title().isEmpty() && !channel.title().equalsIgnoreCase(app.label)))) {
            changed = captureExpandedText(app, channel, extras, posted.getPostTime(), false, false);
        } else {
            String body = text(extras.getCharSequence(Notification.EXTRA_TEXT), 2000);
            if (body.isEmpty()) {
                body = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), 2000);
            }
            String sender = channel.kind() == RecipientRules.Context.PRIVATE && !channel.title().isEmpty()
                    ? channel.title() : null;
            changed = capture(app, channel, channel.title(), sender, stripNotificationCount(app, body), posted.getPostTime(), 0);
        }
        if (changed) {
            Reminders.scheduleNext(this);
            TaskStore.signalChange(this);
        }
    }

    private static boolean isDingTalkAppTitle(String title) {
        return title.isEmpty() || title.equals(App.DINGTALK.label) || title.equalsIgnoreCase("DingTalk");
    }

    private boolean captureExpandedText(App app, Channel channel, Bundle extras, long postTime,
                                        boolean summary, boolean confirmedPrivate) {
        String title = channel.title();
        Pattern senderPrefix = app == App.WECHAT ? WECHAT_COUNT_PREFIX
                : app == App.DINGTALK ? DINGTALK_SENDER_PREFIX : null;
        Set<String> bodies = new LinkedHashSet<>();
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                String raw = stripNotificationCount(app, text(line, 2000));
                if (raw.isEmpty()) continue;
                String body = stripSenderPrefix(senderPrefix, raw, title);
                // Without a private-chat flag, a summary must identify the same contact on every line.
                if (summary && !confirmedPrivate && body.equals(raw)) return false;
                if (!body.isEmpty()) bodies.add(body);
            }
        }
        if (summary && app == App.DINGTALK && bodies.isEmpty()) {
            String raw = stripNotificationCount(app, text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), 2000));
            if (raw.isEmpty()) raw = stripNotificationCount(app, text(extras.getCharSequence(Notification.EXTRA_TEXT), 2000));
            String body = stripSenderPrefix(senderPrefix, raw, title);
            if ((confirmedPrivate || !body.equals(raw)) && !body.isEmpty() && raw.lines().count() == 1) bodies.add(body);
        } else if (!summary) {
            String preview = stripSenderPrefix(senderPrefix,
                    stripNotificationCount(app, text(extras.getCharSequence(Notification.EXTRA_TEXT), 2000)), title);
            if (bodies.isEmpty()) {
                String expanded = stripSenderPrefix(senderPrefix,
                        stripNotificationCount(app, text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), 2000)), title);
                String body = expanded.isEmpty() ? preview : expanded;
                if (!body.isEmpty()) bodies.add(body);
            } else if (!preview.isEmpty()
                    && bodies.stream().noneMatch(body -> body.contains(preview) || preview.contains(body))) {
                bodies.add(preview);
            }
        }
        boolean changed = false;
        String sender = title.isEmpty() ? null : title;
        for (String body : bodies) {
            changed |= capture(app, channel, title, sender, body, postTime, 0);
        }
        return changed;
    }

    private static String stripNotificationCount(App app, String body) {
        return app == App.DINGTALK ? DINGTALK_COUNT_PREFIX.matcher(body).replaceFirst("") : body;
    }

    private static String stripSenderPrefix(Pattern pattern, String body, String title) {
        if (pattern == null) return body;
        Matcher prefix = pattern.matcher(body);
        return !title.isEmpty() && prefix.find() && title.equals(MonitoringRules.normalize(prefix.group(1)))
                ? body.substring(prefix.end()) : body;
    }

    private static String removeLeadingQuestions(String body, TaskRules.Match task) {
        String suffix = body;
        Matcher question = LEADING_QUESTION.matcher(suffix);
        while (question.find()) {
            suffix = suffix.substring(question.end());
            question.reset(suffix);
        }
        if (suffix.matches("^(?:不对|说错了|改(?:到|成|为)|我说岔了)[\\s,，。].*")) return body;
        return !suffix.equals(body) && task.equals(TaskRules.notificationTask(suffix)) ? suffix : body;
    }

    private static boolean hasAffiliation(String conversation, String title) {
        String contact = MonitoringRules.normalize(title);
        String name = MonitoringRules.normalize(conversation);
        return !contact.isEmpty() && name.startsWith(contact)
                && DINGTALK_AFFILIATION.matcher(name.substring(contact.length()).strip()).matches();
    }

    private static boolean allFromContact(List<Message> messages, String contact) {
        return !contact.isEmpty() && messages.stream().allMatch(message -> {
            Person person = message.getSenderPerson();
            return person != null && contact.equals(MonitoringRules.normalize(text(person.getName(), 120)));
        });
    }

    private static Boolean groupEvidence(App app, Bundle extras, List<Message> messages, String conversation,
                                         String title, boolean affiliation) {
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return true;
        if (affiliation && !messages.isEmpty() && allFromContact(messages, MonitoringRules.normalize(title))) return false;
        boolean hasGroupFlag = extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION);
        boolean dingTalkPrivate = app == App.DINGTALK && hasGroupFlag;
        String expected = MonitoringRules.normalize(conversation.isEmpty() ? title : conversation);
        String firstSender = "";
        for (Message message : messages) {
            Person person = message.getSenderPerson();
            String sender = person == null ? "" : MonitoringRules.normalize(text(person.getName(), 120));
            if (sender.isEmpty()) continue;
            if ((!dingTalkPrivate && !conversation.isEmpty() && !expected.equals(sender))
                    || (!firstSender.isEmpty() && !firstSender.equals(sender))) return true;
            firstSender = sender;
        }
        return dingTalkPrivate || (hasGroupFlag && !firstSender.isEmpty() && expected.equals(firstSender)) ? Boolean.FALSE : null;
    }

    private boolean captureMessage(App app, Channel channel, Message message, long postTime) {
        long timestamp = message.getTimestamp();
        if (isStale(message, postTime)) return false;
        Person person = message.getSenderPerson();
        String sender = person == null ? "" : text(person.getName(), 120);
        if (sender.isEmpty()) {
            return false;
        }
        String source = channel.title().isEmpty() || channel.title().equals(sender)
                ? sender : channel.title() + " · " + sender;
        String body = stripNotificationCount(app, text(message.getText(), 2000));
        long received = timestamp > 0 && timestamp <= postTime ? timestamp : postTime;
        return capture(app, channel, source, sender, body, received, timestamp);
    }

    private static boolean isStale(Message message, long postTime) {
        long timestamp = message.getTimestamp();
        return timestamp > 0 && timestamp < postTime - RECENT_MESSAGE;
    }

    private boolean captureFreshPreview(App app, Channel channel, Bundle extras, List<Message> stale, long postTime) {
        String title = channel.title();
        Pattern prefix = app == App.WECHAT ? WECHAT_COUNT_PREFIX
                : app == App.DINGTALK ? DINGTALK_SENDER_PREFIX : null;
        String preview = stripSenderPrefix(prefix,
                stripNotificationCount(app, text(extras.getCharSequence(Notification.EXTRA_TEXT), 2000)), title);
        if (preview.isEmpty()) return false;
        String normalized = MonitoringRules.normalize(preview);
        for (Message message : stale) {
            String old = MonitoringRules.normalize(stripNotificationCount(app, text(message.getText(), 2000)));
            if (!old.isEmpty() && (old.contains(normalized) || normalized.contains(old))) return false;
        }
        return capture(app, channel, title, title, preview, postTime, 0);
    }

    private boolean capture(App app, Channel channel, String source, String sender, String body,
                            long received, long messageTime) {
        boolean changed = false;
        if (channel.kind() == RecipientRules.Context.PRIVATE) {
            for (String task : TaskRules.independentlyScheduledTasks(body)) {
                changed |= captureTask(app, channel, source, sender, task, channel.route(task), received, messageTime);
            }
        } else {
            List<RecipientRules.Decision> decisions = channel.routes(body);
            for (int i = 0; i < decisions.size(); i++) {
                var decision = decisions.get(i);
                // Preserve the old key for the first task; give each additional item its own identity.
                changed |= captureTask(app, channel, source, sender, i == 0 ? body : decision.text(),
                        decision, received, messageTime);
            }
        }
        return changed;
    }

    private boolean captureTask(App app, Channel channel, String source, String sender, String body,
                                RecipientRules.Decision decision, long received, long messageTime) {
        if (decision.action() != RecipientRules.Action.AUTO) return false;
        String taskText = decision.text();
        TaskRules.Match task = TaskRules.notificationTask(taskText);
        if (task == null) {
            return false;
        }
        if (app == App.WECHAT && channel.kind() == RecipientRules.Context.PRIVATE) {
            String cleaned = removeLeadingQuestions(taskText, task);
            if (messageTime == 0 && !cleaned.equals(taskText)) body = cleaned;
            taskText = cleaned;
        }
        Long due = task.dueAt(Instant.ofEpochMilli(received).atZone(ZoneId.systemDefault()));
        String label = source.isEmpty() ? app.label : app == App.WECHAT ? source : app.label + " · " + source;
        return TaskStore.get(this).add(label, body, taskText, due, messageTime,
                new TaskStore.Origin(app.packageName, sender), received) != null;
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
