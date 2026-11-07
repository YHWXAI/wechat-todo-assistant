package com.wechattodo.assistant;

public final class ChatRecognitionCases {
    private record Case(String label, String message, boolean expected) {}

    public static void main(String[] args) {
        Case[] cases = {
            new Case("polite request", "请明天下午3点发送设计稿", true),
            new Case("casual request", "中午你买点东西回来", true),
            new Case("bring something", "回来的时候带点菜不？", true),
            new Case("follow up", "麻烦跟进一下这个订单", true),
            new Case("remember", "记得带合同", true),
            new Case("direct request", "你先联系一下客户", true),
            new Case("polite question request", "你可以帮我取一下快递吗？", true),
            new Case("daily chat", "楼下便利店今天开门吗？", false),
            new Case("reported other assignee", "我让小王下去买材料了", false),
            new Case("missing groceries", "家里牛奶和面包都没有", false),
            new Case("status question", "你买了吗？", false),
            new Case("status question with complement", "你买好了吗？", false),
            new Case("object status question", "你带身份证了吗？", false),
            new Case("describing purchase", "你买的牛奶很好喝", false),
            new Case("describing received file", "你发的图片我收到了", false),
            new Case("describing possession", "你带的伞在门口", false),
            new Case("completed statement", "今天我已经把合同发了", false),
            new Case("completed request", "请查收，文件我已经发送了", false),
            new Case("completed but new action", "资料我已经发了，请你明天确认一下", true),
            new Case("completed but mixed action", "牛奶买好了，你再买点面包回来", true),
            new Case("cancel instruction", "明天不用发合同了", false),
            new Case("quoted old request", "昨天他说请明天发送设计稿", false),
            new Case("casual question", "你买什么？", false),
            new Case("ordinary chat", "明天一起吃饭吗？", false)
            ,new Case("unused object", "请带上不用的样品", true)
            ,new Case("different object", "请你发一下别的文件", true)
            ,new Case("cancel scheduled action", "不用明天寄样品了", false)
            ,new Case("cancel direct action", "你明天别买牛奶了", false)
            ,new Case("negative reminder", "你不要忘了带合同", true)
            ,new Case("request a status check", "请查一下牛奶买好了没有", true)
            ,new Case("confirm completed object", "请你确认我已经发的文件", true)
        };
        int failures = 0;
        for (Case item : cases) {
            boolean actual = TaskRules.isChatTask(item.message());
            if (actual != item.expected()) {
                failures++;
                System.out.println("FAIL " + item.label() + " expected=" + item.expected() + " actual=" + actual);
            }
        }
        System.out.println((cases.length - failures) + "/" + cases.length + " recognition cases passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
