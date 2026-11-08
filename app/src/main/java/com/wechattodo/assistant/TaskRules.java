package com.wechattodo.assistant;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskRules {
    private static final String VERBS =
            "发布|重发|发(?!生|现|烧|票)|回(?!家|来|去)|交|提交|提供|处理|确认|审核|整理|完成|开会|参会|参加|"
            + "修改|更新|安排|准备|跟进|联系|付款|支付|打电话|查|检查|核对|带|取|买|签|给|"
            + "做|煮|洗|拿|接|送|寄|收(?!据|入|益)|约|订|预约|写|读|看|打印|复印|扫描|"
            + "上传|下载|保存|转发|转账|缴费|报销|报名|登记|填写|补交|"
            + "搬|换|修|收拾|打扫|清理|关|锁|叫醒|浇|喂|遛|倒|擦|添|备份|同步|打包|"
            + "领取|复查|复诊|吃|服药|去|跑|还|借|补|记下|取消|用|请假|"
            + "告诉|问|通知|提醒|私聊|验证|测试|部署|上线|发货|对接|对账|对一下|对齐|验收|回访|报到|"
            + "打卡|签到|复习|预习|背|抄|朗读|收听|测量|充电|拔|戴|摘|加|打开|关闭|"
            + "消毒|晾|泡|冻|炖|烤|择|切|清点|归还|上交|缴纳|续费|续签|退|退款|购买|订购|"
            + "补办|寄送|发起|加入|进入|核实|补充|预订|清空|截图|录制|录音|拍|询问|转告|"
            + "起床|休息|运动|锻炼|回家|回来|出门|睡觉|熬夜|迟到|了解|放|弄|"
            + "导出|统计|评估|复核|协助|裁|标红|重启|录入|重命名|誊|卸|拖|挪|移(?=到|进|出|上|下|走)|装(?=入|进|上|好|回)|录下|转(?=我|给)|开(?=个?发票)|让|盖|监考|作个废|留档|催|称|走|来(?=取|拿|接|签)"
            + "|到(?=[\\p{IsHan}]{1,12}(?:开会|签到|报到|集合|取件|复诊))";
    private static final Pattern ACTION = Pattern.compile("^(?:" + VERBS + ")");
    private static final Pattern DISPOSAL = Pattern.compile("^(?:把|将(?!于|在|会|要|来|近|由)).{1,32}?(?:" + VERBS + ")");
    private static final Pattern BARE_CHORE = Pattern.compile(
            "^(?:做饭|做(?:早|午|晚)饭|买菜|取快递|拿快递|接孩子|倒垃圾|浇花|喂猫|喂狗|遛狗|洗衣服|洗碗)$");
    private static final Pattern REMINDER = Pattern.compile(
            "^(?:(?:(?:一定|千万|麻烦)?(?:记得|记着|记住)(?:要)?|(?:千万)?(?:别忘|不要忘|别漏)(?:了|记)?"
            + "|务必|切记|提醒我|(?:温馨|友情)?提醒(?:一下)?[:：])[:：]?\\s*)+");
    private static final Pattern REQUEST = Pattern.compile(
            "^(?:(?:能不能|可不可以|能否|可否|可以|能|是否方便|方便的话|方不方便|方便|有没有空)?\\s*"
            + "(?:(?:还)?请(?!问|教|假|勿)(?:你|您|大家|各位)?|麻烦(?:你|您)?|帮我|帮忙|辛苦(?:你|您)?|劳烦(?:你|您)?|拜托(?:你|您)?|有劳(?:你|您)?)"
            + "|能不能|可不可以|能否|可否|是否方便|方不方便|方便的话|方便(?=帮|把|给|回|打|联系|下[来去]|上[来去])|帮(?=把|将)|可以|能|有没有空"
            + "|给我(?=回|打|发|转)|需要你|我需要你|我(?:想|要)(?:请|麻烦)(?:你|您)|得(?=[\\p{IsHan}])|必须|待办[:：]?)\\s*");
    private static final Pattern MODIFIER = Pattern.compile(
            "^(?:(?:先|再|然后|接着|另外|还有|顺便|顺手|顺路|尽快|按时|及时|一定要|也要|得要"
            + "|第一时间|抽空|线上|线下|改成|改到|改为|需要(?!你)|要(?!不|是|么|求)|给我|帮我|帮(?:个)?忙)\\s*)+");
    private static final Pattern MENTION = Pattern.compile("^(?:@[^\\s@,。!?;]{1,40}\\s+)+");
    private static final Pattern RECIPIENT = Pattern.compile("^(?:你们|你|您|大家|各位|咱们)\\s*");
    private static final Pattern LIST_MARK = Pattern.compile(
            "^(?:[-*•·☐□]|\\[[ xX]?\\]|\\((?:[0-9]{1,2}|[一二三四五六七八九十]+)\\)"
            + "|[0-9]{1,2}[.)、](?!\\d)|[一二三四五六七八九十]+、)\\s*");
    private static final Pattern REQUEST_PREFACE = Pattern.compile(
            "^(?:紧急[:：]\\s*|对了[、:：]?\\s*|想(?=麻烦|请)|[\\p{IsHan}]{1,6}(?:老师|师傅|姐|哥|总)[:：]\\s*|看(?=能|可否|方不方便)|请问\\s*(?=能不能|能否|可不可以|可否))");
    private static final Pattern TASK_TOPIC = Pattern.compile(
            "^(?:(?:这|那|该|最后|第[一二三四五六七八九十0-9]+)[\\p{IsHan}0-9]{1,16}?(?=请|麻烦|拜托|记得)"
            + "|(?:样稿|文件|附件|报价单|报告|报表)(?=发(?:给)?我))");
    private static final Pattern CHAT_TOPIC = Pattern.compile("^小票(?=发(?:给)?我)");
    private static final Pattern ADDRESSED_MOVE = Pattern.compile(
            "^[\\p{IsHan}0-9]{1,24}?(?=(?:你|您)(?:先)?(?:挪|移|搬))");
    private static final String BRING_BACK = "捎[^,。!?;]{1,20}回来(?:吧|呗|哈|呀)?";
    private static final Pattern TRIP_ACTION = Pattern.compile("^(?:顺走|" + BRING_BACK + "$)");
    private static final String EVENT_TARGET =
            "(?:(?:到(?:了|达)?|进了|进到)[\\p{IsHan}0-9]{0,14}?(?:站|口|门|室|区|台|场|馆|店|家|公司|仓库|楼下|楼上)"
            + "|刷卡进(?:门|楼)|(?:拿到|收到)[\\p{IsHan}0-9]{1,16}?)";
    private static final String EVENT_REQUEST = "(?:你|您|记得|请|麻烦|帮|通知我|喊我|叫我|给我|发个消息|拍(?:一?张|照))";
    private static final Pattern EVENT_CONTEXT = Pattern.compile(
            "^(?:(?:等(?:你|您)?)?" + EVENT_TARGET + "(?:以后|之后|后|了)?"
            + "(?=\\s*(?:就?" + EVENT_REQUEST + "|[。!]*$))"
            + "|(?:(?:来|去)?(?:开会|培训|上课|开庭|拿钥匙)|路过[\\p{IsHan}0-9]{0,14}?)(?:的时候|时)"
            + "|(?:回|去)[\\p{IsHan}0-9]{1,12}?(?:途中|路上)"
            + "|等[\\p{IsHan}0-9]{1,16}?(?:结束|完成|处理完|散场)(?:以后|之后|后|再)"
            + "|(?:要是|如果)?(?:腾得出手|腾得出空|抽得出空)(?:的时候|的话)?(?=帮|请|麻烦|把)"
            + "|(?:经过|路过)[\\p{IsHan}]{1,16}?(?=" + BRING_BACK + "[。!]*$))"
            + "(?:就(?=" + EVENT_REQUEST + "))?\\s*");
    private static final Pattern RELAY_OR_CALL = Pattern.compile(
            "^(?:跟|和|向|给)[\\p{IsHan}0-9]{1,14}?(?:说一声|打个招呼|(?:回|打)(?:一下|个)?电话)"
            + "|^(?:回|打)(?:一下|个)?(?:[\\p{IsHan}0-9]{1,14}的)?电话");
    private static final String CONDITION_ACTION = "(?=请|麻烦|记得|别忘|顺手|顺便)";
    private static final Pattern CONDITION = Pattern.compile(
            "^(?:(?:如果|要是|假如|一旦|遇到|等|用完).{1,40}?(?:就\\s*|" + CONDITION_ACTION + ")"
            + "|(?:收到|拿到|取到|签收|[^,。!?;]{1,16}没).{1,20}?(?:以后|之后|前|后)\\s*" + CONDITION_ACTION + ")");
    private static final Pattern DIRECT_REQUEST = Pattern.compile(
            "^(?:发(?:给)?我|回(?:复)?我|转(?:发|给)我|通知我|联系我|告诉我|叫我|拍给我|私聊我|喊我)");
    private static final Pattern CHAT_IMPERATIVE = Pattern.compile("(?:一下(?:吧|哈|呀)?|吧)\\s*$");
    private static final Pattern RELATIVE = Pattern.compile(
            "大后天|后天|明天|明儿|明早|明晚|今晨|今天|今晚|今早");
    private static final Pattern CHINESE_DATE = Pattern.compile(
            "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})(?:日|号)?(?!\\d)");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)");
    private static final Pattern DAY_OF_MONTH = Pattern.compile(
            "(?<![\\d第])(0?[1-9]|[12][0-9]|3[01])\\s*[号日](?!\\s*(?:楼|栋|幢|座|线|站|室|床|桌|车|柜|箱|门|口|版|件|票|文件|订单|任务|样品|报告))");
    private static final Pattern SCOPED_MONTH_DAY = Pattern.compile(
            "(本|这|下)(?:个)?月(0?[1-9]|[12][0-9]|3[01])[号日](?!\\s*[楼栋线室柜])");
    private static final Pattern SCOPED_YEAR_DATE = Pattern.compile(
            "(今|明|后)年(\\d{1,2})月(\\d{1,2})[日号]");
    private static final Pattern SHORT_DATE = Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/]|\\s*(?:份|件|米|元|公斤|斤|成|倍|名|人|页|张))");
    private static final Pattern FULL_CHINESE_DATE = Pattern.compile("\\d{4}年\\d{1,2}月\\d{1,2}[日号]");
    private static final Pattern WEEKDAY = Pattern.compile("(?:(本|这|下下|下|上)(?:个)?)?(?:周|星期|礼拜)([一二三四五六日天1-7])");
    private static final Pattern MONTH_END = Pattern.compile("(?:(?:本|这|下)(?:个)?月(?:底|末|最后一天)|月底|月末)");
    private static final Pattern YEAR_END = Pattern.compile("(?:今年|本年)?(?:年底|年末)");
    private static final String AMOUNT = "(?:[0-9]{1,4}|[零〇一二两三四五六七八九十]{1,3})";
    private static final String DURATION_VALUE = "(半|" + AMOUNT + ")(?:个)?(半)?(分钟|刻钟|小时|钟头|天|周)(半)?"
            + "(?:(" + AMOUNT + ")分钟)?";
    private static final Pattern DURATION = Pattern.compile(
            "(?<![\\d零〇一二两三四五六七八九十])(?:过)?" + DURATION_VALUE + "(?:以后|之后|之内|后|内)");
    private static final Pattern AFTER_DURATION = Pattern.compile(
            "过" + DURATION_VALUE + "(?=\\s*(?:[,，。!?]|$|提醒我|记得|请|再))");
    private static final Pattern DEADLINE = Pattern.compile("(?:最晚(?:到|在)?|最迟(?:到|在)?|截止(?:到|至)?|截至|不晚于)\\s*[:：]?\\s*");
    private static final Pattern RESCHEDULE = Pattern.compile(
            "^(?:(?:时间|现在|现)\\s*)?改(?:到|为|成)\\s*[:：]?\\s*");
    private static final Pattern ORIGINAL_SCHEDULE = Pattern.compile("^(?:原定|原计划)");
    private static final Pattern WITHDRAWAL = Pattern.compile(
            "^(?:(?:先|暂时|那就)?(?:不用了|不必了|算了)|(?:刚通知|刚接到通知)?(?:会议|会)取消了)$");
    private static final Pattern MEETING_ACTION = Pattern.compile(
            "(?:开会|参会)(?=$|[。!\\s])|(?:参加|召开|主持|出席).{0,10}会议");
    private static final Pattern WITHDRAWAL_ACTION = Pattern.compile(
            "^(?:([\\p{IsHan}0-9]{1,16}?)(?=先|暂时))?(?:先|暂时)?(?:别|不要|不用)(?:再)?([\\p{IsHan}]{1,12}?)(?:了)?$");
    private static final Pattern ALTERNATIVE_QUESTION = Pattern.compile("还是(?:我|你|他|她|我们|你们)(?:来|去|做|发|买|处理)");
    private static final Pattern STATUS_QUESTION = Pattern.compile(
            "(?:了(?!解)|过(?!来|去)|完成).{0,20}(?:没有|没|吗|么|了吧)\\s*[?。]?\\s*$");
    private static final Pattern END_PARTICLE = Pattern.compile("了\\s*[!。]?\\s*$");
    private static final Pattern QUESTION_END = Pattern.compile("(?:吗|么|没有|没|呢|[?])\\s*[。]?\\s*$");
    private static final Pattern CANCELLATION_NOTICE = Pattern.compile(
            "^(?:取消(?:了|吧|$)|改天(?:吧|再说|再议)|(?:先|暂时)?(?:算了|作废|不用了|不必了|无需了))");
    private static final Pattern NEGATION = Pattern.compile(
            "^(?:(?:先|暂时|今天|明天|后天|你|您)*(不用|不必|无需|不需要|别|不要|千万别|切勿|请勿)"
            + "(?:你|您|再|先|今天|明天|后天)*)");
    private static final String QUOTE_LABEL = "(?:[^,，。!?;\\n]{0,22}?(?:原文|原话)|引用|转述)(?:是|为|说)?\\s*[:：]";
    private static final Pattern QUOTED_BLOCK = Pattern.compile(
            "(?:^|(?<=[。!?;\\n]))\\s*(?:" + QUOTE_LABEL + "\\s*)?(?:“[^”]*”|「[^」]*」|‘[^’]*’)");
    private static final Pattern QUOTED_CONTENT = Pattern.compile(
            "“[^”]*”|「[^」]*」|『[^』]*』|‘[^’]*’|《[^》]*》|\"[^\"\\n]*\"");
    private static final Pattern FORWARDED_BLOCK = Pattern.compile(
            "(?s)^(?:【(?:转发的)?聊天记录】).*?(?:【(?:聊天记录|转发)结束】|\\z)");
    private static final Pattern QUOTED_TAIL = Pattern.compile(
            "^(?:【(?:转发的)?聊天记录】|(?:他|她|他们|她们|对方|客户)(?:刚才)?(?:说|问|提到)\\s*[:：])");
    private static final Pattern QUOTED = Pattern.compile(
            "^(?:[>「“]|" + QUOTE_LABEL
            + "|(?:昨天|前天|之前|刚才)?(?:他|她|他们|她们|对方|群里(?:有人)?|有人|客户)"
            + "(?:刚才|之前|昨天|上次|已经)?(?:说|问|提到)\\s*[:,，])");
    private static final Pattern CHECK_ACTION = Pattern.compile("^(?:查|确认|核对|看一下|检查|看看)");
    private static final Pattern REPORTED = Pattern.compile(
            "^(?:(?:昨天|前天|之前|上次|刚才).{0,12}(?:说|问)|(?:原话|引用|转述)(?:是|为|说)"
            + "|(?:已经|刚刚|刚才|早就)(?:帮你|替你|给你)?(?:" + VERBS + "))");
    private static final Pattern SELF_REPORT = Pattern.compile("^(?:我们?|他(?:们)?|她(?:们)?)(?!需要你)");
    private static final Pattern DESCRIPTION = Pattern.compile("^(?:" + VERBS + ")(?:到?的|了|过(?!来|去))");
    private static final Pattern FINISHED = Pattern.compile(
            "^(?:" + VERBS + ").{0,24}(?:了[啦哈啊]?|已经完成|已完成)\\s*[?。]?\\s*$");
    private static final Pattern EXPLICIT_REQUEST = Pattern.compile(
            "请|麻烦|拜托|帮我|帮忙|劳烦|辛苦|有劳|需要你|必须|待办|给我");
    private static final Pattern STATUS_UPDATE = Pattern.compile(
            "顺利结束|(?:已|已经)(?:归档|发布|恢复|结束|签收)|(?:功能|服务|系统)(?:已)?(?:上线|下线)$"
            + "|说明(?:网络|系统|服务)|的(?:速度|样子|声音)|是(?:他|她|我)(?:自己)?的计划");
    private static final Pattern NON_ACTION = Pattern.compile(
            "^(?:那天|那时候|那个时候|那个[^,。!?;]{1,20}(?:最后|当时)(?:没|未|已经)|当时|以前|原来|已经|刚才|很|还记得|是|不是|什么|谁|哪[天个里儿]|怎么|多少|几点|为什么)"
            + "|^(?:一下|一声|这个|那个|这件事|那件事|哦|哈|啊|好的|谢谢|了呀|了哦)[!。]*$");
    private static final String NOW_MARKER = "(?:现在(?:就)?|立刻|马上|当场|当下|这会儿|此刻|这就)";
    private static final Pattern IMMEDIATE = Pattern.compile("^" + NOW_MARKER + "(?!的|有|是|已经|还|不|没|能不能)");
    private static final Pattern IMMEDIATE_DISPOSAL = Pattern.compile(
            "^(?:把|将)[^,。!?;]{1,24}?" + NOW_MARKER + "(?=递|" + VERBS + ")");
    private static final Pattern ON_SITE = Pattern.compile(
            "^(?:我|我们)(?:已经|刚)?(?:到|在|就在)[\\p{IsHan}0-9]{1,16}(?:了|旁边|楼下|门口)$");
    private static final Pattern LIVE_HANDOFF = Pattern.compile(
            "^(?:[下上][来去](?:递|拿|接|取|开门)|打给我|给我(?:回|打)(?:个|一下)?电话|看一眼)");
    private static final Pattern QUICK_FIX = Pattern.compile(
            "^(?:补|填|写|签)(?:一)?个(?:名字|姓名|字)就(?:行|好|可以)(?:了)?[。!]*$");
    private static final Pattern SELF_OFFER = Pattern.compile(
            "^(?:我)?(?:可以|能|愿意)(?:先)?(?:借给你|替你|帮你|代你)");
    private static final String HOUR = "[0-9零〇一二两三四五六七八九十]{1,3}";
    private static final Pattern TIME = Pattern.compile(
            "(凌晨|清晨|早晨|早上|上午|中午|午后|下午|傍晚|晚上|晚间|夜里|早|晚)?\\s*(?<!\\d)("
                    + HOUR + ")(?::([0-9]{2})(?![0-9])|[点时](?:(半|一刻|三刻)|("
                    + HOUR + ")分|([0-9]{2}|[零〇][一二三四五六七八九]|[一二三四五]?十[一二三四五六七八九]?)"
                    + "(?![0-9零〇一二两三四五六七八九十号楼栋座线份件个元米]))?(?:整(?!理|改|修|顿|合)|钟(?!头))?)");
    private static final String CALENDAR_DATE =
            SCOPED_YEAR_DATE.pattern() + "|" + SCOPED_MONTH_DAY.pattern() + "|" + NUMERIC_DATE.pattern()
            + "|" + CHINESE_DATE.pattern() + "|" + SHORT_DATE.pattern() + "|" + RELATIVE.pattern()
            + "|" + WEEKDAY.pattern() + "|" + MONTH_END.pattern() + "|" + YEAR_END.pattern()
            + "|" + DAY_OF_MONTH.pattern();
    private static final Pattern DATE_REFERENCE = Pattern.compile(
            "(?:(?:" + CALENDAR_DATE + ")(?:\\s*(?:" + TIME.pattern() + "|上午|下午|早上|晚上))?"
            + "|" + TIME.pattern() + ")\\s*(?:会议|沟通|通话|录音)?的"
            + "|" + NUMERIC_DATE.pattern() + "\\s*(?:版本|版(?!式|面|权|图))");
    private static final Pattern CONTEXT = Pattern.compile(
            "^(?:(?:(?:在|于|从)?(?:" + CALENDAR_DATE + "|" + DURATION.pattern() + "|" + AFTER_DURATION.pattern() + "|" + TIME.pattern() + ")"
            + "|下班|回家|回来|出门|有空|到家|到公司|周末|假期|到时候|过会儿?|待会儿?|等会儿?|一会儿?|稍后"
            + "|清晨|早晨|早上|上午|中午|午后|下午|傍晚|晚上|下周(?![一二三四五六日天1-7]))(?:的话|之前|以前|以后|之后|的时候|时候|路上|开始|前|后)?\\s*"
            + "|(?:在|于)?(?:睡觉|吃饭|饭|午饭|晚饭|早餐|开会|开完会|会|封箱|上班|放学|起床|起来)(?:之前|以后|之后|的时候|时候|前|后)\\s*)+");
    private static final Pattern SCHEDULED_VISIT = Pattern.compile(
            "^[\\p{IsHan}]{1,12}?(?:" + CALENDAR_DATE + "|" + TIME.pattern() + "|上午|下午|中午|晚上|晚点)"
            + "(?:上午|下午|中午|晚上|" + TIME.pattern() + ")?(?:过来|来|到)[\\p{IsHan}]{0,12}$");
    private static final Pattern SENTENCE_END = Pattern.compile("[。!]+\\s*$");
    private static final Pattern EARLY = Pattern.compile("明早|今早|今晨|清晨|早晨|早上|上午");
    private static final LocalTime DEFAULT_TIME = LocalTime.of(18, 0);

    private TaskRules() {}

    record Match(String clause, String trailingTime) {
        Long dueAt(ZonedDateTime reference) {
            return deadline(this, reference, false);
        }
    }

    record Assessment(Match match, boolean blocksFallback) {}

    static Match notificationTask(String message) {
        return assessNotification(message).match();
    }

    static Assessment assessNotification(String message) {
        return assess(message, false);
    }

    static boolean isChatTask(String message) {
        return assess(message, true).match() != null;
    }

    static String classificationText(String message) {
        Match match = assess(message, true).match();
        return match == null ? stripQuotedText(normalize(message)) : match.clause();
    }

    private static Assessment assess(String message, boolean chat) {
        String original = normalize(message);
        String unquoted = stripQuotedText(original);
        boolean blocked = !unquoted.equals(original);
        String[] sentences = unquoted.split("(?<=[。!?;\\r\\n])");
        for (int sentenceIndex = 0; sentenceIndex < sentences.length; sentenceIndex++) {
            String sentence = sentences[sentenceIndex];
            if (QUOTED_TAIL.matcher(sentence.strip()).find()) {
                blocked = true;
                break;
            }
            if (QUOTED.matcher(stripContext(sentence.strip())).find()) {
                blocked = true;
                continue;
            }
            String carry = "";
            boolean onSite = false;
            String[] parts = sentence.split("[,，]");
            for (int index = 0; index < parts.length; index++) {
                String item = LIST_MARK.matcher(parts[index].strip()).replaceFirst("");
                String clause = MENTION.matcher(item).replaceFirst("");
                if (clause.isEmpty()) {
                    continue;
                }
                String following = followingClause(parts, index, sentences, sentenceIndex);
                String rescheduled = rescheduledTime(following);
                String combined = carry + REQUEST_PREFACE.matcher(clause).replaceFirst("");
                if (!rescheduled.isEmpty()) {
                    combined = ORIGINAL_SCHEDULE.matcher(combined).replaceFirst("");
                }
                String withoutTime = stripContext(combined);
                String withoutRecipient = RECIPIENT.matcher(withoutTime).replaceFirst("");
                String withCondition = stripContext(withoutRecipient);
                String afterEvent = EVENT_CONTEXT.matcher(withCondition).replaceFirst("");
                boolean followUp = !afterEvent.equals(withCondition);
                if (followUp) {
                    afterEvent = RECIPIENT.matcher(afterEvent).replaceFirst("");
                }
                String unconditioned = CONDITION.matcher(afterEvent).replaceFirst("");
                String body = TASK_TOPIC.matcher(unconditioned).replaceFirst("");
                if (chat) {
                    body = CHAT_TOPIC.matcher(body).replaceFirst("");
                }
                boolean addressed = !withoutRecipient.equals(withoutTime) || !item.equals(clause);
                boolean timed = !withoutTime.equals(combined) || !withCondition.equals(withoutRecipient);
                String previous = index == 0 ? "" : stripSentenceEnd(parts[index - 1].strip());
                // An upcoming visit can give the following request context without setting its deadline.
                if (!followUp && SCHEDULED_VISIT.matcher(previous).matches()) {
                    String move = ADDRESSED_MOVE.matcher(body).replaceFirst("");
                    if (!move.equals(body)) {
                        body = RECIPIENT.matcher(move).replaceFirst("");
                        followUp = true;
                    }
                }
                onSite |= ON_SITE.matcher(stripSentenceEnd(clause)).matches();
                if (body.isEmpty() || isInstructionLead(body)) {
                    carry = combined + " ";
                    continue;
                }
                if (isImmediateOrOffer(body, timed || followUp, onSite) || withdraws(combined, following)) {
                    blocked = true;
                    carry = "";
                    continue;
                }
                boolean groupInstruction = timed && item.startsWith("@所有人");
                if (isTaskClause(body, timed || followUp, addressed || !afterEvent.equals(unconditioned), chat, groupInstruction)) {
                    String trailing = rescheduled;
                    if (trailing.isEmpty()) {
                        trailing = trailingTime(parts, index, following, combined);
                    }
                    return new Assessment(new Match(combined, trailing), false);
                }
                carry = "";
            }
        }
        return new Assessment(null, blocked);
    }

    private static String stripQuotedText(String text) {
        String withoutForward = FORWARDED_BLOCK.matcher(text).replaceFirst("");
        String withoutBlocks = QUOTED_BLOCK.matcher(withoutForward).replaceAll("。");
        return QUOTED_CONTENT.matcher(withoutBlocks).replaceAll(" ");
    }

    private static boolean isImmediateOrOffer(String body, boolean followUp, boolean onSite) {
        Matcher request = REQUEST.matcher(body);
        String action = request.find() ? body.substring(request.end()) : body;
        if (SELF_OFFER.matcher(body).lookingAt()) {
            return true;
        }
        if (REMINDER.matcher(body).lookingAt() || body.startsWith("待办")) {
            return false;
        }
        return IMMEDIATE.matcher(body).lookingAt() || IMMEDIATE.matcher(action).lookingAt()
                || IMMEDIATE_DISPOSAL.matcher(action).lookingAt()
                || (!followUp && (QUICK_FIX.matcher(action).matches() || (onSite && LIVE_HANDOFF.matcher(action).lookingAt())));
    }

    private static boolean isInstructionLead(String body) {
        Matcher request = REQUEST.matcher(body);
        String afterRequest = request.find() ? body.substring(request.end()) : body;
        Matcher reminder = REMINDER.matcher(afterRequest);
        String remainder = reminder.find() ? afterRequest.substring(reminder.end()) : afterRequest;
        return !remainder.equals(body) && stripInstructionContext(remainder).isEmpty();
    }

    private static String followingClause(String[] parts, int index, String[] sentences, int sentenceIndex) {
        if (index + 1 < parts.length) {
            return stripSentenceEnd(parts[index + 1].strip());
        }
        for (int next = sentenceIndex + 1; next < sentences.length; next++) {
            String text = sentences[next].strip();
            if (!text.isEmpty()) {
                return stripSentenceEnd(text.split("[,，]", 2)[0]);
            }
        }
        return "";
    }

    private static boolean withdraws(String original, String following) {
        if (WITHDRAWAL.matcher(following).matches()) {
            return !following.endsWith("取消了") || MEETING_ACTION.matcher(original).find();
        }
        Matcher verb = WITHDRAWAL_ACTION.matcher(following);
        if (!verb.matches()) {
            return false;
        }
        String object = verb.group(1);
        String action = verb.group(2);
        return (ACTION.matcher(action).find() || action.equals("打")) && original.contains(action)
                && (object == null || original.contains(object));
    }

    private static String rescheduledTime(String following) {
        Matcher reschedule = RESCHEDULE.matcher(following);
        if (!reschedule.find()) {
            return "";
        }
        String time = following.substring(reschedule.end());
        return (hasDate(time) || TIME.matcher(time).find()) && stripContext(time).isEmpty() ? time : "";
    }

    private static String trailingTime(String[] parts, int index, String following, String clause) {
        boolean explicit = DEADLINE.matcher(following).lookingAt();
        if (following.isEmpty() || (!explicit && index + 2 != parts.length)) {
            return "";
        }
        if (stripContext(following).isEmpty()) {
            return following;
        }
        // A start time and its explicitly stated cutoff can belong to the same instruction.
        if (explicit && clause.contains("开始") && (hasDate(following) || TIME.matcher(following).find())) {
            return following;
        }
        return "";
    }

    private static String stripContext(String text) {
        Matcher deadline = DEADLINE.matcher(text);
        if (deadline.lookingAt()) {
            String afterMarker = text.substring(deadline.end());
            String afterTime = CONTEXT.matcher(afterMarker).replaceFirst("");
            if (!afterTime.equals(afterMarker)) {
                return afterTime;
            }
        }
        return CONTEXT.matcher(text).replaceFirst("");
    }

    private static String stripSentenceEnd(String text) {
        return SENTENCE_END.matcher(text).replaceFirst("").stripTrailing();
    }

    private static String stripInstructionContext(String text) {
        String content = stripSentenceEnd(text);
        return ACTION.matcher(content).matches() ? text : stripContext(text);
    }

    private static boolean isTaskClause(String body, boolean timed, boolean addressed, boolean chat, boolean groupInstruction) {
        body = REQUEST_PREFACE.matcher(body).replaceFirst("");
        if (body.startsWith("请问") || body.startsWith("请教") || body.startsWith("系统消息")
                || REPORTED.matcher(body).find() || CANCELLATION_NOTICE.matcher(body).find()) {
            return false;
        }

        Matcher request = REQUEST.matcher(body);
        boolean requested = request.find();
        String requestBody = stripInstructionContext(requested ? body.substring(request.end()).stripLeading() : body);
        String reminderBody = MODIFIER.matcher(requestBody).replaceFirst("");
        Matcher reminder = REMINDER.matcher(reminderBody);
        boolean reminded = reminder.find();
        String instruction = reminded ? reminderBody.substring(reminder.end()).stripLeading() : requestBody;
        String withoutTime = stripInstructionContext(instruction);
        String action = stripInstructionContext(MODIFIER.matcher(withoutTime).replaceFirst(""));
        if (action.isEmpty() || CANCELLATION_NOTICE.matcher(action).find()
                || REPORTED.matcher(action).find() || SELF_REPORT.matcher(action).find()) {
            return false;
        }

        Matcher negation = NEGATION.matcher(action);
        if (negation.find()) {
            String word = negation.group(1);
            String prohibited = action.substring(negation.end()).stripLeading().replaceFirst("^(?:空腹|单独|在[\\p{IsHan}]{1,12}?(?:里|中))", "");
            boolean preventative = word.equals("千万别") || word.equals("切勿") || word.equals("请勿")
                    || (reminded && (word.equals("别") || word.equals("不要")));
            return preventative && !QUESTION_END.matcher(action).find()
                    && (ACTION.matcher(prohibited).find() || DISPOSAL.matcher(prohibited).find());
        }

        boolean checkRequested = (requested || reminded) && CHECK_ACTION.matcher(action).find();
        boolean explicitRequest = reminded || (requested && EXPLICIT_REQUEST.matcher(request.group()).find());
        if ((STATUS_QUESTION.matcher(action).find() && !checkRequested)
                || (!checkRequested && ALTERNATIVE_QUESTION.matcher(action).find())
                || (QUESTION_END.matcher(action).find() && !requested && !checkRequested)
                || (!checkRequested && (FINISHED.matcher(action).find() || DESCRIPTION.matcher(action).find()))
                || (!checkRequested && !explicitRequest && STATUS_UPDATE.matcher(stripSentenceEnd(action)).find())) {
            return false;
        }

        String content = stripSentenceEnd(action);
        boolean disposal = DISPOSAL.matcher(content).find();
        if (disposal && !requested && !reminded && !groupInstruction && END_PARTICLE.matcher(action).find()) {
            return false;
        }
        if (reminded && content.length() >= 2 && !NON_ACTION.matcher(content).find()) {
            return true;
        }
        boolean modified = !action.equals(instruction);
        return disposal || DIRECT_REQUEST.matcher(content).find()
                || ((requested || timed) && RELAY_OR_CALL.matcher(content).find())
                || (timed && TRIP_ACTION.matcher(content).lookingAt())
                || (ACTION.matcher(content).find() && (requested || timed || modified
                || (chat && (addressed || BARE_CHORE.matcher(content).matches() || CHAT_IMPERATIVE.matcher(content).find()))));
    }

    static Long dueFromChat(String message, ZonedDateTime scanned) {
        Match match = assess(message, true).match();
        if (match == null) {
            return null;
        }
        Long due = deadline(match, scanned, true);
        return due != null && due > scanned.toInstant().toEpochMilli() ? due : null;
    }

    private static Long deadline(Match match, ZonedDateTime reference, boolean absoluteOnly) {
        String instruction = CONDITION.matcher(match.clause()).replaceFirst("");
        String task = DATE_REFERENCE.matcher(instruction).replaceAll(" ");
        String trailing = match.trailingTime();
        Matcher marker = DEADLINE.matcher(task);
        String explicit = trailing.isEmpty()
                ? (marker.find() ? task.substring(marker.end()) : "")
                : DEADLINE.matcher(trailing).replaceFirst("");
        boolean explicitDate = hasDate(explicit);
        boolean explicitDuration = durationIn(explicit) != null;
        String dateText = explicitDate || explicitDuration ? explicit : task;
        String clockText = explicitDate || TIME.matcher(explicit).find() ? explicit : task;
        Matcher yearDate = SCOPED_YEAR_DATE.matcher(dateText);
        Matcher monthDay = SCOPED_MONTH_DAY.matcher(dateText);
        Matcher shortDate = SHORT_DATE.matcher(dateText);
        Matcher numeric = NUMERIC_DATE.matcher(dateText);
        Matcher chinese = CHINESE_DATE.matcher(dateText);
        Matcher relative = RELATIVE.matcher(dateText);
        Matcher weekday = WEEKDAY.matcher(dateText);
        Matcher monthEnd = MONTH_END.matcher(dateText);
        Matcher yearEnd = YEAR_END.matcher(dateText);
        Matcher dayOfMonth = DAY_OF_MONTH.matcher(dateText);
        Matcher duration = durationIn(explicit.isEmpty() ? task : explicit);

        try {
            LocalDate date = reference.toLocalDate();
            String period = null;
            if (numeric.find()) {
                date = dateFrom(numeric, date);
            } else if (!absoluteOnly && yearDate.find()) {
                int offset = switch (yearDate.group(1)) {
                    case "明" -> 1;
                    case "后" -> 2;
                    default -> 0;
                };
                date = LocalDate.of(date.getYear() + offset, Integer.parseInt(yearDate.group(2)),
                        Integer.parseInt(yearDate.group(3)));
            } else if (!absoluteOnly && monthDay.find()) {
                YearMonth month = YearMonth.from(date).plusMonths(monthDay.group(1).equals("下") ? 1 : 0);
                date = month.atDay(Integer.parseInt(monthDay.group(2)));
            } else if (chinese.find() && (!absoluteOnly || FULL_CHINESE_DATE.matcher(chinese.group()).matches())) {
                date = dateFrom(chinese, date);
            } else if (absoluteOnly) {
                return null;
            } else if (shortDate.find()) {
                date = nextMonthDay(date, Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));
            } else if (duration != null) {
                return relativeDeadline(duration, reference);
            } else if (relative.find()) {
                String word = relative.group();
                if (word.equals("大后天")) {
                    date = date.plusDays(3);
                } else if (word.equals("后天")) {
                    date = date.plusDays(2);
                } else if (word.startsWith("明")) {
                    date = date.plusDays(1);
                }
                if (word.endsWith("早") || word.endsWith("晚")) {
                    period = word.substring(1);
                }
            } else if (weekday.find()) {
                date = weekdayDate(weekday, date);
            } else if (monthEnd.find()) {
                if (monthEnd.group().startsWith("下")) {
                    date = date.plusMonths(1);
                }
                date = date.with(TemporalAdjusters.lastDayOfMonth());
            } else if (yearEnd.find()) {
                date = date.with(TemporalAdjusters.lastDayOfYear());
            } else if (dayOfMonth.find()) {
                date = nextDayOfMonth(date, Integer.parseInt(dayOfMonth.group(1)));
            } else if (!TIME.matcher(clockText).find()) {
                return null;
            }
            if (!explicitDate && !clockText.equals(task) && period == null) {
                Matcher originalClock = TIME.matcher(task);
                if (originalClock.find()) {
                    period = originalClock.group(1);
                }
            }
            LocalDateTime due = dateTimeFrom(date, clockText, period);
            // A same-day bare hour with a cutoff can mean afternoon if its morning reading has passed.
            Matcher clock = TIME.matcher(clockText);
            if (!absoluteOnly && period == null && date.equals(reference.toLocalDate()) && dateText.contains("今天")
                    && clock.find() && clock.group(1) == null && clock.group(3) == null
                    && clockText.substring(clock.end()).matches("(?:之|以)?前.*")
                    && due.getHour() >= 1 && due.getHour() < 12 && due.isBefore(reference.toLocalDateTime())
                    && due.plusHours(12).isAfter(reference.toLocalDateTime())) {
                due = due.plusHours(12);
            }
            return due.atZone(reference.getZone()).toInstant().toEpochMilli();
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean hasDate(String text) {
        return SCOPED_YEAR_DATE.matcher(text).find() || SCOPED_MONTH_DAY.matcher(text).find()
                || NUMERIC_DATE.matcher(text).find() || CHINESE_DATE.matcher(text).find() || SHORT_DATE.matcher(text).find()
                || RELATIVE.matcher(text).find() || WEEKDAY.matcher(text).find() || MONTH_END.matcher(text).find()
                || YEAR_END.matcher(text).find() || DAY_OF_MONTH.matcher(text).find();
    }

    private static LocalDate weekdayDate(Matcher weekday, LocalDate date) {
        String week = weekday.group(1);
        String day = weekday.group(2);
        DayOfWeek target = DayOfWeek.of("日".equals(day) || "天".equals(day) ? 7 : number(day));
        if (week == null) {
            return date.with(TemporalAdjusters.nextOrSame(target));
        }
        long weekOffset = switch (week) {
            case "上" -> -1;
            case "下下" -> 2;
            case "下" -> 1;
            default -> 0;
        };
        return date.with(target).plusWeeks(weekOffset);
    }

    private static Matcher durationIn(String text) {
        Matcher duration = DURATION.matcher(text);
        if (duration.find()) {
            return duration;
        }
        Matcher afterDuration = AFTER_DURATION.matcher(text);
        return afterDuration.find() ? afterDuration : null;
    }

    private static long relativeDeadline(Matcher duration, ZonedDateTime reference) {
        String amount = duration.group(1);
        boolean halfOnly = "半".equals(amount);
        int units = halfOnly ? 0 : number(amount);
        String unit = duration.group(3);
        ZonedDateTime due = switch (unit) {
            case "分钟" -> reference.plusMinutes(units);
            case "刻钟" -> reference.plusMinutes(15L * units);
            case "小时", "钟头" -> reference.plusHours(units);
            case "天" -> reference.plusDays(units);
            default -> reference.plusWeeks(units);
        };
        if (halfOnly || duration.group(2) != null || duration.group(4) != null) {
            due = switch (unit) {
                case "分钟" -> due.plusSeconds(30);
                case "刻钟" -> due.plusMinutes(7).plusSeconds(30);
                case "小时", "钟头" -> due.plusMinutes(30);
                case "天" -> due.plusHours(12);
                default -> due.plusDays(3).plusHours(12);
            };
        }
        if (duration.group(5) != null) {
            due = due.plusMinutes(number(duration.group(5)));
        }
        return due.toInstant().toEpochMilli();
    }

    private static LocalDate dateFrom(Matcher match, LocalDate today) {
        String year = match.group(1);
        int month = Integer.parseInt(match.group(2));
        int day = Integer.parseInt(match.group(3));
        if (year != null) {
            return LocalDate.of(Integer.parseInt(year), month, day);
        }
        return nextMonthDay(today, month, day);
    }

    private static LocalDate nextMonthDay(LocalDate today, int month, int day) {
        MonthDay requested = MonthDay.of(month, day);
        int candidateYear = today.getYear();
        while (!requested.isValidYear(candidateYear) || requested.atYear(candidateYear).isBefore(today)) {
            candidateYear++;
        }
        return requested.atYear(candidateYear);
    }

    private static LocalDate nextDayOfMonth(LocalDate today, int day) {
        YearMonth month = YearMonth.from(today);
        if (day < today.getDayOfMonth()) {
            month = month.plusMonths(1);
        }
        while (day > month.lengthOfMonth()) {
            month = month.plusMonths(1);
        }
        return month.atDay(day);
    }

    private static LocalDateTime dateTimeFrom(LocalDate date, String text, String defaultPeriod) {
        Matcher time = TIME.matcher(text);
        if (!time.find()) {
            if ("早".equals(defaultPeriod) || EARLY.matcher(text).find()) {
                return date.atTime(9, 0);
            }
            return date.atTime(DEFAULT_TIME);
        }
        String period = time.group(1) == null ? defaultPeriod : time.group(1);
        int hour = number(time.group(2));
        int minute = 0;
        if (time.group(3) != null) {
            minute = Integer.parseInt(time.group(3));
        } else if (time.group(4) != null) {
            minute = switch (time.group(4)) {
                case "半" -> 30;
                case "一刻" -> 15;
                default -> 45;
            };
        } else if (time.group(5) != null || time.group(6) != null) {
            minute = number(time.group(5) != null ? time.group(5) : time.group(6));
        }
        if (hour == 24 && minute == 0) {
            return date.plusDays(1).atStartOfDay();
        }
        if (period != null) {
            switch (period) {
                case "晚上", "晚间", "夜里", "晚" -> {
                    if (hour == 12) return date.plusDays(1).atTime(0, minute);
                    if (hour >= 1 && hour < 12) hour += 12;
                }
                case "下午", "午后", "傍晚" -> {
                    if (hour >= 1 && hour < 12) hour += 12;
                }
                case "中午" -> {
                    if (hour >= 1 && hour <= 5) hour += 12;
                }
                case "凌晨", "清晨", "早晨", "早上", "上午", "早" -> {
                    if (hour == 12) hour = 0;
                }
            }
        }
        return date.atTime(hour, minute);
    }

    private static int number(String value) {
        if (value.matches("[0-9]+")) {
            return Integer.parseInt(value);
        }
        int ten = value.indexOf('十');
        if (ten >= 0 && value.length() <= 3 && ten <= 1 && value.length() - ten <= 2) {
            int tens = ten == 0 ? 1 : digit(value.charAt(0));
            int ones = ten + 1 == value.length() ? 0 : digit(value.charAt(ten + 1));
            return tens * 10 + ones;
        }
        if (value.length() == 2 && (value.charAt(0) == '零' || value.charAt(0) == '〇')) {
            return digit(value.charAt(1));
        }
        if (value.length() == 1) {
            return digit(value.charAt(0));
        }
        throw new NumberFormatException();
    }

    private static int digit(char value) {
        int digit = "零一二三四五六七八九".indexOf(value);
        if (value == '两') return 2;
        if (value == '〇') return 0;
        if (digit < 0) throw new NumberFormatException();
        return digit;
    }

    private static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).replace("\r\n", "\n").trim();
    }
}
