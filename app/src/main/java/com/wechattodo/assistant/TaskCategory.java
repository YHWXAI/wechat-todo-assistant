package com.wechattodo.assistant;

import java.util.regex.Pattern;

enum TaskCategory {
    DAILY, WORK;

    private static final Pattern WORK_CONTEXT = Pattern.compile(
            "客户|甲方|乙方|供应商|财务(?:部|组|同事)?|人事(?:部|组|同事)?|采购(?:部|组|同事|负责人)|(?:给|找|跟|和)采购"
            + "|项目(?:组|进度|方案|报告|文件|材料|文档|预算|会议|交付|验收|排期)|工作(?:群|汇报|交接|计划|任务|会议)"
            + "|部门例会|生产环境|测试环境|代码审查|版本发布");
    private static final Pattern DAILY_CONTEXT = Pattern.compile(
            "家里|家用|家人|家务|孩子|宝宝|小孩|幼儿园|家长会|放学|作业|同学|课表|校服|考试|上课|课后|补课"
            + "|挂号|门诊|复诊|医院|看牙|体检|疫苗|吃药|服药|药费|药房|药店|医保|社保卡"
            + "|买菜|菜市场|做饭|做[早午晚]饭|准备[早午晚]饭|准备早餐|做早餐|做晚餐|取快递|拿快递|寄快递|网购|收快递"
            + "|遛狗|喂猫|喂狗|浇花|倒垃圾|洗碗|洗衣服|健身|看电影|驾校|搬家|房租|租房|房东|物业费|水电费|燃气费|小区物业|家庭聚餐");
    private static final Pattern WORK_TASK = Pattern.compile(
            "会议|开会|参会|周会|晨会|例会|校样|样稿|设计稿|工牌|工单|报价|合同|对账|报销|发票|开票"
            + "|日报|周报|月报|报表|投标|验收|排班|面试|发货|样品|库存|仓库|出差|考勤|排期|部署|交接班|展台|展厅|评审|请假"
            + "|(?<![a-z0-9])(?:jira|crm|kpi|okr|ppt|sprint|pull request|code review)(?![a-z0-9])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WORK_CHAT = Pattern.compile(
            "(?:工作|公司|部门|项目|研发|销售|运营|财务|采购|客户|供应商)[^群·]{0,12}群|项目组|项目对接");

    static TaskCategory classify(String message, String source) {
        String task = TaskRules.classificationText(message);
        if (WORK_CONTEXT.matcher(task).find()) {
            return WORK;
        }
        if (DAILY_CONTEXT.matcher(task).find()) {
            return DAILY;
        }
        if (WORK_TASK.matcher(task).find()) {
            return WORK;
        }
        // A work chat can resolve a generic instruction, but cannot override the task itself.
        if (source != null) {
            int sender = source.lastIndexOf(" · ");
            String conversation = sender < 0 ? source : source.substring(0, sender);
            if (WORK_CHAT.matcher(conversation).find()) {
                return WORK;
            }
        }
        return DAILY;
    }
}
