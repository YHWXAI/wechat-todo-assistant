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
        String direct = "明天请提交一份报表，记得附上附件。";
        Channel privateNotification = MonitoringRules.notification(List.of(), "", "新联系人", null);
        equal(Context.PRIVATE, privateNotification.kind());
        equal(Action.AUTO, privateNotification.route(direct).action());
        equal(Action.CONFIRM, privateNotification.route("要是仓库到货了，记得给我留两盒。").action());
        equal(Action.CONFIRM, privateNotification.route("下次回家路上顺便把咖啡豆带回来哈。").action());
        equal(Action.AUTO, privateNotification.route("明天晚上六点有空的话，把备用钥匙拿给我。").action());
        equal(Action.IGNORE, privateNotification.route("你已经把样品寄出去了吗？").action());
        equal(Context.PRIVATE, MonitoringRules.notification(CONFIGURED, "", GROUP.title(), false).kind());
        equal(Context.PRIVATE, MonitoringRules.visible(List.of(), "新联系人").kind());
        equal(direct, MonitoringRules.visible(List.of(), "新联系人").route(direct).text());

        Channel unconfiguredGroup = MonitoringRules.notification(CONFIGURED, "未配置的群", GROUP.title(), true);
        equal("未配置的群", unconfiguredGroup.title());
        equal(Context.GROUP, unconfiguredGroup.kind());
        for (String text : List.of("大家不要忘了点点一键三连哦，感谢", "可以用这个改不？")) {
            equal(Action.IGNORE, unconfiguredGroup.route(text).action());
            equal(Action.IGNORE, privateNotification.route(text).action());
        }
        equal(Action.CONFIRM, unconfiguredGroup.route(direct).action());
        equal(Action.AUTO, unconfiguredGroup.route("@所有人 明早九点开会").action());
        equal(Context.GROUP, MonitoringRules.notification(CONFIGURED, "", PRIVATE.title(), true).kind());
        equal(Context.GROUP, MonitoringRules.visible(List.of(), "未配置的群（23）").kind());
        equal("设计评审群", MonitoringRules.visible(CONFIGURED, "设计评审群（23）").title());
        equal("设计评审群", MonitoringRules.notification(CONFIGURED, "设计评审群", "小王", null).title());
        equal("设计评审群", MonitoringRules.notification(CONFIGURED, "", "设计评审群", null).title());

        equal(Action.CONFIRM, MonitoringRules.visible(CONFIGURED, SHARED.title()).route("@李老师 明天请发合同").action());
        equal(Action.CONFIRM, MonitoringRules.visible(CONFIGURED, SHARED.title()).route("@李老师 明天下午四点来办公室拿回执").action());
        equal(Action.AUTO, MonitoringRules.visible(CONFIGURED, EMPTY.title()).route("@所有人 明早九点开会").action());
        equal(Action.IGNORE, MonitoringRules.visible(CONFIGURED, GROUP.title()).route("@李老师 明天请发合同").action());
        equal(Action.AUTO, MonitoringRules.visible(CONFIGURED, GROUP.title()).route("@小李 明天请发合同").action());
        Channel group = MonitoringRules.visible(CONFIGURED, GROUP.title());
        equal(Action.IGNORE, group.route("@小王 明天请把合同发给我").action());
        equal(Action.IGNORE, group.route("@所有人 小王负责发报价").action());
        equal(Action.CONFIRM, group.route("明天请把合同发给我").action());
        equal(Action.CONFIRM, group.route("@所有人 周三下午两点开会，只有值班的人参加。").action());
        equal(Action.CONFIRM, group.route("设计的同学周三上午十点前把海报源文件给我一下。").action());

        equal(Action.IGNORE, group.route("@小李 明天请发合同。算了，不用了").action());
        var mixed = group.route("@小王 明天请把合同发给我。@小李 周五请核对报价");
        equal(Action.AUTO, mixed.action());
        equal("周五请核对报价", mixed.text());
        equal(Action.IGNORE, group.route("明晚七点@小周 把海报贴出来，@小李 知道了就好。").action());
        equal(Action.CONFIRM, group.route("@小李 等表决结束记得把投票底稿另存一份。").action());
        var withTopic = group.route("维修单麻烦@小李 明晚六点前发到我邮箱。");
        equal(Action.AUTO, withTopic.action());
        equal(true, withTopic.text().contains("维修单"));

        String personalReply = "明天下午三点发运单号给我，别发群里。";
        equal(Action.IGNORE, group.route(personalReply).action());
        equal(Action.AUTO, group.route("@小李 " + personalReply).action());
        equal(Action.AUTO, unconfiguredGroup.route("每个人都要在明天下午三点前把运单号发给我，别发群里。").action());
        equal(Action.AUTO, privateNotification.route(personalReply).action());
        equal(Action.IGNORE, group.route("明早九点把名单交过来，谁有空谁弄一下。").action());
        equal(Action.IGNORE, group.route("收到，谢谢。" + personalReply).action());
        var remaining = group.route("明早九点把名单交过来，谁有空谁弄一下。明晚六点把灯光记录发给我。");
        equal(Action.CONFIRM, remaining.action());
        equal("明晚六点把灯光记录发给我。", remaining.text());
        equal(Action.IGNORE, unconfiguredGroup.route("@小李 明早十点把签收照片发我。").action());
        equal(Action.CONFIRM, privateNotification.route("@小李 明早十点把签收照片发我。").action());
        equal(Action.CONFIRM, MonitoringRules.visible(CONFIGURED, SHARED.title())
                .route("@李老师 明早十点把签收照片发我。").action());
        equal(Action.AUTO, unconfiguredGroup.route("@小李 @所有人 明早十点交回执。").action());
        var multiple = group.routes("@小李 明早八点把出库名单发我。@小周 明早十点收快递。@小李 周二下午四点之前把摇号回执发给教练。");
        equal(2, multiple.size());
        equal(true, multiple.get(0).text().contains("出库名单"));
        equal(true, multiple.get(1).text().contains("摇号回执"));
        equal(Action.IGNORE, group.route("@所有人 明早十点交扫描件，小李这次不用提交。").action());
        var afterExemption = group.routes("@所有人 明早九点前把点名册交前台。小李不用交。周二下午五点前把签收凭证发给我。");
        equal(1, afterExemption.size());
        equal(Action.AUTO, afterExemption.get(0).action());
        equal(false, afterExemption.get(0).text().contains("点名册"));
        equal(true, afterExemption.get(0).text().contains("签收凭证"));
        equal(Action.IGNORE, group.route("@所有人 明早八点把演出分工表发我，除了小李都需要发。").action());
        var afterUpload = group.routes("@所有人 明早九点上传通行证明；小李不用上传。周二下午五点十分把试剂箱登记页补齐。");
        equal(1, afterUpload.size());
        equal(Action.AUTO, afterUpload.get(0).action());
        equal(true, afterUpload.get(0).text().contains("试剂箱登记页"));
        equal(false, afterUpload.get(0).text().contains("通行证明"));
        equal(Action.AUTO, group.route("@所有人 明天下午一点发售后台账，只有小周这次不用发。").action());

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
                    equal(Context.PRIVATE, MonitoringRules.notification(CONFIGURED, "", "未知会话", null).kind());
                    continue;
                }
                Channel channel = new Channel("模拟会话", kind, missingAlias ? unconfigured : verified);
                Action expected = switch (fields[0]) {
                    // N01, H23 and V15 expected a candidate for an unverified explicit group mention.
                    // V08 names the user as exempt from an all-member request.
                    case "V03", "V08", "V18", "N01", "H23", "V15" -> Action.IGNORE;
                    default -> Action.valueOf(fields[2]);
                };
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
