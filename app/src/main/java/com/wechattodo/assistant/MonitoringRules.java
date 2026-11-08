package com.wechattodo.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class MonitoringRules {
    enum App {
        WECHAT("微信", "com.tencent.mm"), WECOM("企业微信", "com.tencent.wework"),
        QQ("QQ", "com.tencent.mobileqq"), DINGTALK("钉钉", "com.alibaba.android.rimet"),
        FEISHU("飞书", "com.ss.android.lark"), TELEGRAM("Telegram", "org.telegram.messenger"),
        WHATSAPP("WhatsApp", "com.whatsapp");

        final String label;
        final String packageName;

        App(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }

        static App fromPackage(String packageName) {
            for (App app : values()) if (app.packageName.equals(packageName)) return app;
            return null;
        }
    }

    record Conversation(String title, boolean group, Set<String> aliases, boolean sharedAlias) {
        Conversation {
            title = normalize(title);
            aliases = Set.copyOf(aliases);
        }
    }

    record Channel(String title, RecipientRules.Context kind, RecipientRules recipients) {
        RecipientRules.Decision route(String body) {
            return recipients.evaluate(body, kind);
        }
    }

    private static final Pattern MEMBER_COUNT = Pattern.compile("\\s*\\([0-9]{1,5}\\)$");
    private static final RecipientRules UNCONFIGURED = new RecipientRules(Set.of(), Set.of());

    private MonitoringRules() {}

    static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).replace('\u200b', ' ').strip();
    }

    static Channel notification(List<Conversation> configured, boolean groupsEnabled,
                                String conversation, String title, Boolean group) {
        String name = normalize(conversation);
        // Prefer the structured conversation name. The title can instead contain a sender or a summary.
        if (name.isEmpty()) name = normalize(title);
        Conversation match = find(configured, name);
        if (match != null) {
            if (Boolean.TRUE.equals(group) && !match.group()) return null;
            return channel(match, groupsEnabled);
        }
        if (Boolean.FALSE.equals(group)) {
            return new Channel(name, RecipientRules.Context.PRIVATE, UNCONFIGURED);
        }
        return null;
    }

    static Channel visible(List<Conversation> configured, boolean groupsEnabled, String title) {
        String name = normalize(title);
        Conversation match = find(configured, name);
        if (match != null) return channel(match, groupsEnabled);

        String withoutCount = MEMBER_COUNT.matcher(name).replaceFirst("").strip();
        if (!withoutCount.equals(name)) {
            Conversation group = find(configured, withoutCount);
            return group != null && group.group() ? channel(group, groupsEnabled) : null;
        }
        return null;
    }

    private static Conversation find(List<Conversation> configured, String name) {
        for (Conversation item : configured) if (item.title().equals(name)) return item;
        return null;
    }

    private static Channel channel(Conversation conversation, boolean groupsEnabled) {
        if (conversation.group() && !groupsEnabled) return null;
        Set<String> aliases = conversation.aliases();
        RecipientRules recipients = aliases.isEmpty() ? UNCONFIGURED
                : new RecipientRules(aliases, conversation.sharedAlias() ? aliases : Set.of());
        return new Channel(conversation.title(), conversation.group() ? RecipientRules.Context.GROUP
                : RecipientRules.Context.PRIVATE, recipients);
    }
}
