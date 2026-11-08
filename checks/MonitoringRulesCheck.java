package com.wechattodo.assistant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.wechattodo.assistant.MonitoringRules.App;
import com.wechattodo.assistant.MonitoringRules.Channel;
import com.wechattodo.assistant.MonitoringRules.Conversation;
import com.wechattodo.assistant.RecipientRules.Action;
import com.wechattodo.assistant.RecipientRules.Context;

final class MonitoringRulesCheck {
    private static final Conversation GROUP = new Conversation("设计评审群", true, Set.of("小李", "李敏", "小 李"), false);
    private static final Conversation SHARED = new Conversation("另一个设计群", true, Set.of("李老师"), true);
    private static final Conversation EMPTY = new Conversation("邻里群", true, Set.of(), false);
    private static final Conversation PRIVATE = new Conversation("好友小陈", false, Set.of(), false);
    private static final List<Conversation> CONFIGURED = List.of(GROUP, SHARED, EMPTY, PRIVATE);
    private static int checked;

    public static void main(String[] args) throws Exception {
        for (App app : App.values()) equal(app, App.fromPackage(app.packageName));
        equal(null, App.fromPackage("com.example.messages"));
        equal(null, App.fromPackage("com.tencent.mm.fake"));
        equal(null, App.fromPackage(null));
        equal(null, MonitoringRules.notification(CONFIGURED, "没添加的群", "小王", true));
        equal(null, MonitoringRules.notification(CONFIGURED, "没添加的群", "设计评审群", true));
        equal(null, MonitoringRules.notification(CONFIGURED, "", "未知会话", null));
        equal(null, MonitoringRules.notification(CONFIGURED, "", "好友小陈", true));
        equal(null, MonitoringRules.visible(CONFIGURED, "没添加的群（23）"));
        equal(null, MonitoringRules.visible(CONFIGURED, "好友小陈(11)"));
        equal(Context.PRIVATE, MonitoringRules.visible(CONFIGURED, "好友小陈").kind());
        equal(Context.PRIVATE, MonitoringRules.notification(CONFIGURED, "", "好友小陈", null).kind());
        equal(Context.PRIVATE, MonitoringRules.notification(CONFIGURED, "", "已确认的私聊", false).kind());
        equal(Context.GROUP, MonitoringRules.notification(CONFIGURED, "设计评审群", "小王", false).kind());
        equal("设计评审群", MonitoringRules.visible(CONFIGURED, "设计评审群（23）").title());
        equal("设计评审群", MonitoringRules.notification(CONFIGURED, "设计评审群", "小王", null).title());
        equal("设计评审群", MonitoringRules.notification(CONFIGURED, "", "设计评审群", null).title());
        String direct = "明天请提交一份报表，记得附上附件。";
        equal(direct, MonitoringRules.visible(CONFIGURED, PRIVATE.title()).route(direct).text());
        equal(Action.CONFIRM, MonitoringRules.visible(CONFIGURED, SHARED.title()).route("@李老师 明天请发合同").action());
        equal(Action.AUTO, MonitoringRules.visible(CONFIGURED, EMPTY.title()).route("@所有人 明早九点开会").action());
        equal(Action.IGNORE, MonitoringRules.visible(CONFIGURED, GROUP.title()).route("@李老师 明天请发合同").action());
        equal(Action.AUTO, MonitoringRules.visible(CONFIGURED, GROUP.title()).route("@小李 明天请发合同").action());
        Channel group = MonitoringRules.visible(CONFIGURED, GROUP.title());
        equal(Action.IGNORE, group.route("@小王 明天请把合同发给我").action());
        equal(Action.IGNORE, group.route("@所有人 小王负责发报价").action());
        equal(Action.CONFIRM, group.route("明天请把合同发给我").action());
        equal(Action.IGNORE, group.route("@小李 明天请发合同。算了，不用了").action());
        var mixed = group.route("@小王 明天请把合同发给我。@小李 周五请核对报价");
        equal(Action.AUTO, mixed.action());
        equal("周五请核对报价", mixed.text());

        if (args.length > 0) replayRecipientFixtures(Path.of(args[0]));
        System.out.println(checked + " monitoring and recipient checks passed");
    }

    private static void replayRecipientFixtures(Path directory) throws Exception {
        var verified = new RecipientRules(Set.of("小李", "李敏", "小 李", "李老师"), Set.of("李老师"));
        var unconfigured = new RecipientRules(Set.of(), Set.of());
        for (String name : List.of("cases.tsv", "holdout.tsv", "final-cases.tsv")) {
            for (String line : Files.readAllLines(directory.resolve(name))) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\t", -1);
                boolean unknown = fields[1].equals("UNKNOWN");
                boolean missingAlias = fields[1].equals("GROUP_UNCONFIGURED");
                Context kind = fields[1].equals("PRIVATE") ? Context.PRIVATE : Context.GROUP;
                if (unknown) {
                    equal(null, MonitoringRules.notification(CONFIGURED, "", "未知会话", null));
                    continue;
                }
                Channel channel = new Channel("模拟会话", kind, missingAlias ? unconfigured : verified);
                Action expected = fields[0].equals("V03") || fields[0].equals("V18")
                        ? Action.IGNORE : Action.valueOf(fields[2]);
                var actual = channel.route(fields[3]);
                if (actual.action() != expected) throw new AssertionError(fields[0] + " " + actual + " expected " + expected);
                if (expected == Action.AUTO) {
                    if (TaskRules.notificationTask(actual.text()) == null || !actual.text().contains(fields[4])
                            || (fields.length > 5 && !fields[5].isEmpty() && actual.text().contains(fields[5]))) {
                        throw new AssertionError(fields[0] + " wrong selected scope " + actual.text());
                    }
                }
                checked++;
            }
        }
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("expected=" + expected + " actual=" + actual);
        checked++;
    }
}
