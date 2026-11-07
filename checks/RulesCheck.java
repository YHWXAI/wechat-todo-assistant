package com.wechattodo.assistant;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class RulesCheck {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZonedDateTime RECEIVED = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, ZONE);

    public static void main(String[] args) {
        check("请明天下午3点发送设计稿", true, "2026-11-08T07:00:00Z");
        check("麻烦今天上午11点半提交文件", true, "2026-11-07T03:30:00Z");
        check("请在2026-12-08 15:45前提交报价", true, "2026-12-08T07:45:00Z");
        check("请11月9号发送合同", true, "2026-11-09T10:00:00Z");
        check("后天上午九点一刻开会", true, "2026-11-09T01:15:00Z");
        check("明早八点记得打电话", true, "2026-11-08T00:00:00Z");
        check("请下午３点发文件", true, "2026-11-07T07:00:00Z");
        check("请帮我准备会议资料", true, null);
        check("请在2月30日提交文件", true, null);
        check("请明天25点发合同", true, null);
        check("明天一起吃饭吗？", false, null);
        check("请问你发了没有？", false, null);
        check("你收到了3条新消息", false, null);
        System.out.println("13 Chinese task and deadline checks passed");
    }

    private static void check(String text, boolean expectedTask, String expectedDue) {
        boolean task = TaskRules.isTask(text);
        Long due = task ? TaskRules.dueAt(text, RECEIVED) : null;
        Long expected = expectedDue == null ? null : Instant.parse(expectedDue).toEpochMilli();
        if (task != expectedTask || !Objects.equals(due, expected)) {
            throw new AssertionError(text + " task=" + task + " due=" + due + " expected=" + expected);
        }
    }
}
