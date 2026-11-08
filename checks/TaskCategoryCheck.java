package com.wechattodo.assistant;

final class TaskCategoryCheck {
    private static final String WORK_GROUP = "星河项目群 · 小李";
    private static final String FAMILY_GROUP = "一家人 · 妈妈";
    private static int checked;

    public static void main(String[] args) {
        expect(TaskCategory.DAILY, FAMILY_GROUP,
                "明天早上五点记得做早饭", "10号去买菜", "睡觉前记得给猫添粮", "记得交水电费",
                "周五上午记得去医院复诊", "明天记得把孩子的体检表发给老师", "周六下午开家长会",
                "月底记得和房东续签合同", "下周帮我报销孩子的药费", "放学路上记得去药房取药",
                "晚上把家长会的会议纪要转给其他家长");
        expect(TaskCategory.WORK, "小陈",
                "明天下午三点前把设计稿发我", "下周二记得去对账", "周五之前提交项目预算",
                "下午四点参加周会", "明早到仓库核对库存", "今晚把会议纪要整理一下",
                "方便时更新一下JIRA里的进度", "周四记得看一下CRM里跟进到哪一步了",
                "下班前请把纸质发票寄出", "请联系供应商确认交货时间", "明天给采购送两份样品");
        expect(TaskCategory.DAILY, WORK_GROUP,
                "回家顺手取快递", "你下班路过菜市场带点西红柿回来", "明天记得带孩子去看牙",
                "工作忙完了，放学后去接孩子", "原话是：「请给客户发送报价。」明天记得交房租",
                "周报不用交了，明天记得喂猫", "记得交小学作业的PPT");
        expect(TaskCategory.WORK, FAMILY_GROUP,
                "请明天给客户订两份午饭", "下班前把周报发给我", "请把项目文件发到共享盘",
                "小区物业费我已经交了，明天你记得联系供应商", "晚上回家前记得把会议纪要发给项目组");
        expect(TaskCategory.WORK, WORK_GROUP, "明天下午帮我把文件发一下", "你带一支蓝色记号笔");
        expect(TaskCategory.DAILY, FAMILY_GROUP, "明天下午帮我把文件发一下", "你带一支蓝色记号笔");
        expect(TaskCategory.DAILY, "一家人 · 项目组小李", "明天记得带钥匙");
        expect(TaskCategory.DAILY, null, "后天帮我带一袋东西", "原话是：「请交周报。」", null);
        System.out.println(checked + " task category examples passed");
    }

    private static void expect(TaskCategory expected, String source, String... messages) {
        for (String message : messages) {
            TaskCategory actual = TaskCategory.classify(message, source);
            if (actual != expected) {
                throw new AssertionError(message + " from " + source + " was " + actual + ", expected " + expected);
            }
            checked++;
        }
    }
}
