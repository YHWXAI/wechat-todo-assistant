package com.wechattodo.assistant;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

// Run with JDK 17 by compiling this file and TaskRules.java together, then running this class.
class TaskRulesCheck {
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-11-07T22:00:00+08:00[Asia/Shanghai]");
    private static int checked, failed;
    private static void test(String text, boolean notification, boolean chat) {
        checked += 2;
        boolean n = TaskRules.notificationTask(text) != null;
        boolean c = TaskRules.isChatTask(text);
        if (n != notification || c != chat) {
            System.err.println("recognition: " + text + " got " + n + "/" + c + " expected " + notification + "/" + chat);
            failed++;
        }
    }
    private static void due(String text, boolean chat, String expected) {
        due(text, chat, expected, NOW);
    }
    private static void due(String text, boolean chat, String expected, ZonedDateTime reference) {
        checked++;
        TaskRules.Match match = TaskRules.notificationTask(text);
        Long millis = chat ? TaskRules.dueFromChat(text, reference) : match == null ? null : match.dueAt(reference);
        var actual = millis == null ? null : Instant.ofEpochMilli(millis).atZone(reference.getZone()).toLocalDateTime().toString();
        if (!Objects.equals(actual, expected)) {
            System.err.println("deadline: " + text + " chat=" + chat + " got " + actual + " expected " + expected);
            failed++;
        }
    }
    public static void main(String[] args) {
        for (String text : new String[] {
            "明天早上五点记得做早饭", "明天早上五点记得准备早饭", "明天，做早饭", "明天记得给猫添粮",
            "记得浇花", "记得复习一下英语", "别忘了吃药", "下班顺路取快递", "下班后顺手把快递拿回来",
            "睡觉前记得锁门", "早上五点起来后记得准备早餐", "请明天下午3点发送设计稿",
            "请你明天下午五点给我打电话", "能不能帮我预约明天的门诊？", "明天上午请核对一下款项到了没？",
            "麻烦打印两份资料", "可以把窗户关一下吗？", "把门锁好", "给我发一下定位",
            "记得早点休息", "务必保留原件", "下周一下午三点交材料", "周五下午四点复诊",
            "星期三记得带电脑", "11月8日早上5点接孩子", "明天需要完成报告",
            "你明天早上五点做早饭", "今天14:30发文件", "明天开会", "请取消会议", "请在2024-01-02上午8点提交报价", "请记得给猫添粮", "下班前把文件发给我",
            "明天不用买牛奶，后天记得取快递", "@小王 明天记得浇花",
            "待办：打印三份合同", "请今天22:13发送真机测试设计稿"
        }) test(text, true, true);
        for (String text : new String[] {"你带一支蓝色记号笔", "你买一个绿色文件夹", "取快递", "做早饭"}) test(text, false, true);
        for (String text : new String[] {
            null, "", "明天早上五点", "你带了蓝色记号笔吗？", "你带了蓝色记号笔吗", "你明天带伞吗？",
            "你买了什么？", "你带了蓝色记号笔", "你买的面包挺好吃", "我明天会做早饭", "我记得明天要做早饭",
            "不用做早饭了", "明天不用买菜了", "先不要交材料", "已经把合同寄出去了", "明天会议取消了",
            "你还记得第一次见面吗？", "记得那天我们第一次见面", "记得哦", "提醒我一下", "谢谢帮忙",
            "我明天准备去买菜", "我已经发给你了", "下午报告发给你了", "这是你买的那本书",
            "昨天你说要发给我", "请问几点了？", "可以了", "明天可能下雨", "明天星期日", "将于明天开会", "把材料发了", "今天付款已完成", "记得今天什么日子", "明天一起吃饭吗？"
        }) test(text, false, false);
        due("明天早上五点记得做早饭", false, "2026-11-08T05:00");
        due("请明天下午3点发送设计稿", false, "2026-11-08T15:00");
        due("下周一下午三点交材料", false, "2026-11-09T15:00");
        due("周五下午四点复诊", false, "2026-11-13T16:00");
        due("11月8日早上5点接孩子", false, "2026-11-08T05:00");
        due("明天早上五点记得做早饭", true, null);
        due("下周一下午三点交材料", true, null);
        due("2026年11月8日早上五点记得做早饭", true, "2026-11-08T05:00");
        due("2026-11-08 05:00做早饭", true, "2026-11-08T05:00");
        due("2026-11-06 05:00做早饭", true, null);
        for (String text : new String[] {
            "是否方便帮我把门关一下？", "可否帮我问一下几点集合？", "有没有空帮我发份报价？",
            "有劳您周五下午把账对一下", "拜托你，下午五点发一下会议纪要", "麻烦，明早叫醒我",
            "一定记得戴头盔", "还请尽快补办门禁卡", "请务必清点库存", "提醒我别迟到",
            "明天提醒我不要空腹吃药", "千万别把验证码发给陌生人", "请勿转发内部文件",
            "最晚周五5点验收", "半小时后提醒我拔充电器", "一小时半后提醒我关火",
            "二十分钟内把数据备份", "下个月底前续签合同", "月底提交发票",
            "再提醒我，下午三点去接小孩", "1. 明早签到", "二、记得打印准考证",
            "请在饭后提醒我吃药", "记得回家", "记住不要熬夜", "记得早点睡",
            "今天不用买菜，后天记得取快递", "明天不必提交旧表，周一下午五点前提交新表",
            "发我一份报价", "回我一下", "能发过来吗？", "明天可以带好吃的吗？",
            "记得给我看一下你发了没有", "需要你核实一下数据", "请安排下周的培训",
            "最晚明天下午五点之前把报价发给我", "把报表发给我，最晚明天下午五点",
            "今天把报表发给我，截止明天下午五点", "明天发我报价，下午五点前",
            "先把报告发给我，明天下午五点再发最终版"
        }) test(text, true, true);
        for (String text : new String[] {
            "你要不要带伞", "要不改天再说", "你今天带伞没", "你发给我了吧？", "可以提交了吗？",
            "收到啦", "我已经安排明天打印", "今天群里说，明天记得发报告",
            "他说，明天记得发报告", "原话是：明天记得做早饭", "「请明天提交报价」",
            "明天先不必发", "记得不用买牛奶", "能不能先不发了", "取消了",
            "记得以前我们去哪吗？", "已经给你转账了", "明天早上五点。", "暂时不用了"
        }) test(text, false, false);
        test("看一下报告吧", false, true);
        test("@小林 带一下样品", false, true);
        due("半小时后提醒我拔充电器", false, "2026-11-07T22:30");
        due("一小时半后提醒我关火", false, "2026-11-07T23:30");
        due("二十分钟内把数据备份", false, "2026-11-07T22:20");
        due("下个月底前续签合同", false, "2026-12-31T18:00");
        due("月底提交发票", false, "2026-11-30T18:00");
        due("今天不用买菜，后天记得取快递", false, "2026-11-09T18:00");
        due("下午三点不用开会，明天早上五点记得做早饭", false, "2026-11-08T05:00");
        due("明天不必提交旧表，周一下午五点前提交新表", false, "2026-11-09T17:00");
        due("今天把报表发给我，截止明天下午五点", false, "2026-11-08T17:00");
        due("明天发我报价，下午五点前", false, "2026-11-08T17:00");
        due("发我报价，明天，记得买菜", false, null);
        due("先把报告发给我，明天下午五点再发最终版", false, null);
        due("一小时后记得看消息", true, null);
        due("今天不需要提交旧表，2026年11月8日早上五点记得做早饭", true, "2026-11-08T05:00");
        due("请把周五的报告发给我", false, null);
        due("请把周五上午的报告发给我，明天早上五点前", false, "2026-11-08T05:00");
        due("请发会议纪要。截止明天下午五点。", false, "2026-11-08T17:00");
        due("今天把报告发给我，最晚三个小时后", false, "2026-11-08T01:00");
        due("下下周五下午四点交材料", false, "2026-11-20T16:00");
        test("提醒我，明天5点，做早饭", true, true);
        test("我想请你核对订单", true, true);
        test("友情提醒：今晚记得充电", true, true);
        test("如果明天下雨就记得带伞", true, true);
        test("假如下雨就关窗", false, true);
        test("如果下雨就会降温", false, false);
        due("提醒我，明天5点，做早饭", false, "2026-11-08T05:00");
        due("如果明天下雨就记得带伞", false, null);
        due("请发会议纪要。\r\n截止明天下午五点。", false, "2026-11-08T17:00");
        test("10号去买菜", true, true);
        due("10号去买菜", false, "2026-11-10T18:00");
        due("10号去买菜", true, null);
        due("10号早上五点买菜", false, "2026-12-10T05:00", NOW.withDayOfMonth(11));
        due("7号去买菜", false, "2026-11-07T18:00");
        due("31号去买菜", false, "2026-12-31T18:00");
        due("3日去买菜", false, "2027-01-03T18:00", NOW.withMonth(12).withDayOfMonth(30));
        test("32号去买菜", false, false);
        test("10号楼开门了", false, false);
        due("请去10号楼取快递", false, null);
        due("请把10号文件发给我", false, null);
        due("请于1月3日上午10点报到", false, "2027-01-03T10:00", NOW.withMonth(12).withDayOfMonth(30));
        due("请于2026年1月3日上午10点报到", false, "2026-01-03T10:00");
        due("请2月29日核对数据", false, "2028-02-29T18:00");
        due("请于2月30日核对数据", false, null);
        due("2026.11.09下午4点请签收", true, "2026-11-09T16:00");
        due("明天下午三点二十交材料", false, "2026-11-08T15:20");
        due("明天下午3点30发报价", false, "2026-11-08T15:30");
        due("明天中午一点零五分请出门", false, "2026-11-08T13:05");
        due("两个半小时后提醒我给车充电", false, "2026-11-08T00:30");
        due("一刻钟后请提醒我关火", false, "2026-11-07T22:15");
        due("明晚24:00记得关掉发布开关", false, "2026-11-09T00:00");
        due("明晚24:10记得关掉发布开关", false, null);
        due("下午3点30件衣服要取回来", false, null);
        for (String text : new String[] {
            "请统计这周的退款单", "给我导出一下本月明细", "这份表麻烦明早核对一遍",
            "样稿发我一份", "放学后记得练琴", "请问能不能帮我开个发票？",
            "原定今天的会议取消了，改成明天下午三点开会", "请安排系统上线",
            "请通知我服务恢复", "记得看系统上线的直播", "原话是：“明天发货。”请确认库存。"
        }) test(text, true, true);
        for (String text : new String[] {
            "今天发布了新版说明", "今天收据已归档", "今天开会顺利结束", "你买过这个牌子",
            "原话是：“请明天给车充电。记得带充电卡。”", "昨天群里有人说，明早要打印报价",
            "下午3点30件衣服要取回来"
        }) test(text, false, false);
        ZonedDateTime morning = NOW.withHour(9);
        for (String text : new String[] {
            "今晚七点记得交接，不用了", "明早请给供应商打电话，先别打了", "今晚八点开会。刚通知会议取消了。",
            "你明天去买菜还是我去", "记得那个项目最后没上线",
            "原话是：‘请把样品寄出。记得附上检测单。’", "【聊天记录】\\n明天请补发合同。"
        }) test(text, false, false);
        for (String text : new String[] {
            "今天请补两份报价，另外下午的会议取消了", "请把会议纪要发我，会议取消了",
            "原话是：“明天寄文件。”麻烦帮我核实地址。", "对了明天八点记得接人",
            "@所有人　下班前把灯都关了", "收到包裹以后记得拍照留档", "门锁没修好前记得走侧门"
        }) test(text, true, true);
        test("你今天把灯都关了", false, false);
        test("小票发我看看", false, true);
        due("明天上午十点开会，改到下午两点", false, "2026-11-08T14:00", morning);
        due("明天上午十点开会。时间改到下午两点。", false, "2026-11-08T14:00", morning);
        due("请在10号交预算，改到12号上午9点", false, "2026-11-12T09:00", morning);
        due("明天上午八点来取样，最晚九点", false, "2026-11-08T09:00", morning);
        due("今天七点到实验室开会，改到明天九点", false, "2026-11-08T09:00", morning);
        due("请从明天上午9点开始核对，最迟11点发我", false, "2026-11-08T11:00", morning);
        due("今晚十二点记得关灯", false, "2026-11-08T00:00", morning);
        due("明天中午十二点发一下航班信息", false, "2026-11-08T12:00", morning);
        due("本月10号记得买菜", false, "2026-11-10T18:00", morning);
        due("下个月10号前请寄样", false, "2026-12-10T18:00", morning);
        due("下个月10号前请寄样", true, null, morning);
        due("明年1月5日上午9点去报到", false, "2027-01-05T09:00", morning);
        due("请于11/10下午五点前提交报价", false, "2026-11-10T17:00", morning);
        due("请打印3/5页", false, null, morning);
        due("这个月最后一天记得交物业费", false, "2026-11-30T18:00", morning);
        due("一小时二十分钟后提醒我换水", false, "2026-11-07T10:20", morning);
        due("过二十分钟提醒我取餐", false, "2026-11-07T09:20", morning);
        due("请把明天下午三点的会议纪要在今天五点前发我", false, "2026-11-07T17:00", morning);
        due("请把明天下午三点的会议纪要在今天早上五点前发我", false, "2026-11-07T05:00", morning);
        due("请把明天下午三点的会议纪要在今天05:00前发我", false, "2026-11-07T05:00", morning);
        due("请把2026年11月10日上午9点会议的纪要发给我", false, null, morning);
        due("请把上周五下午两点的录音发给我", false, null, morning);
        due("请把测试机2026.11.10版本的截图发给我", false, null, morning);
        due("请把测试机2026.11.10版本的截图发给我", true, null, morning);
        for (String text : new String[] {
            "阿哲问「校样能不能带来」，你来开会的时候带一下吧",
            "晚点别来接我，你帮我跟接待说一声我自己过去",
            "桌上的杯子是给前台的，你路过的时候顺走两个",
            "师傅下午来换锁，门口那辆车你挪到隔壁位去吧",
            "到了公交站喊我一声，我把票发你",
            "周三上午方便给仓库回一下电话不"
        }) test(text, true, true);
        for (String text : new String[] {
            "你先帮我把蓝箱子搬到门口。算了，箱子先别搬",
            "明早把工具箱搬到门口，工具箱先别搬",
            "现场插座不够，我包里有个拖线板，可以借给你",
            "我到门口了，门卡还在你手上，方便下来递给我么",
            "你负责的那格签收人还空着，补个名字就可以",
            "麻烦现在把这张截图发给我",
            "请把书包现在递给我一下，我就站在门口",
            "群公告原文：「请核实报到地点。周三发我结果。」"
        }) {
            test(text, false, false);
            checked++;
            if (!TaskRules.assessNotification(text).blocksFallback()) {
                System.err.println("fallback should be blocked: " + text);
                failed++;
            }
        }
        test("明早把工具箱搬到门口，红箱子先别搬", true, true);
        test("师傅下午三点来换锁，门口那辆车你挪到隔壁位去吧", true, true);
        test("等你收到纸质回单，拍张照发给我", true, true);
        due("师傅下午三点来换锁，门口那辆车你挪到隔壁位去吧", false, null);
        due("周三上午十点方便给仓库回一下电话不", false, "2026-11-11T10:00");
        if (failed > 0) throw new AssertionError(failed + " failing examples");
        System.out.println(checked + " assertions passed");
    }
}
