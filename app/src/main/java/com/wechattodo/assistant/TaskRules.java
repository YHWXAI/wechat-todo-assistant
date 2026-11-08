package com.wechattodo.assistant;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskRules {
    private static final String NAMED_MEETING = "开(?:项目|部门|小组|例|早|周|月|总结|碰头|协调|视频|线上|临时|例行)会";
    private static final String VERBS =
            "发布|重发|发(?!生|现|烧|票)|回(?!家|来|去)|交|提交|提供|处理|确认|审核|整理|完成|开会|参会|参加|"
            + "修改|更新|安排|准备|跟进|联系|付款|支付|打电话|查|检查|核对|带|取|买|签|给|"
            + "做|煮|洗|拿|接|送|寄|收(?!据|入|益)|约|订|预约|写|读|看|打印|复印|扫描|"
            + "上传|下载|保存|转发|转账|缴费|报销|报名|登记|填写|补交|"
            + "搬|换|修|贴|印(?=出来|好|一|两)|重传|补齐|补发|补全|缩短|画完|翻(?:一遍|一下)|报(?=一下|一声|个)|分享|填写|填|订|洗|过(?:一遍|一下|一眼)|教|垫(?=一下|付|上)|敲定|结掉|留(?=一|个|份|两|下)|约(?:一下)?复诊|续(?=一下|上)|量(?=一下|一量)|收好|冲销|另存|收拾|打扫|清理|关|锁|打印|扫描|办(?=好|完|一下|手续)|给(?!我|你|您|他|她)(?=[\\p{IsHan}0-9])|记(?=一下|下来|上)|翻(?=下|找)|叫醒|浇|喂|遛|倒|擦|添|备份|同步|打包|"
            + "领取|领(?=.{1,20})|缴(?=了|一下|清|好|完)|理发|量尺寸|传(?=上来|下来|到|给|过来)|装车|装袋|注销|刷(?=漆|一遍)|存(?=到|在|进)|拷(?=一|几|到)|丢(?=给|过来)|捎(?=一|两|点|袋|瓶|给)|"
            + "清(?=一下|干净|掉|完|洗)|找(?=一下|出来|回|好|到|一找|个|[\\p{IsHan}]{1,10}(?:签|盖章|核对|确认|登记))|改(?=成|为|好|一下|掉)|"
            + "复查|复诊|吃|服药|去|跑|还(?!得|是|有|没|想|要|能|可以)|借|补|记下|取消|用|请假|"
            + "告诉|问|通知|提醒|私聊|验证|测试|部署|上线|发货|对接|对账|对一下|对齐|验收|回访|报到|"
            + "打卡|签到|复习|预习|背|抄|朗读|收听|测量|充电|拔|戴|摘|加|打开|关闭|"
            + "消毒|消掉|删掉|递|晾|泡|冻|炖|烤|择|切|清点|归还|上交|缴纳|续费|续签|退|退款|购买|订购|"
            + "补办|寄送|发起|加入|进入|核实|补充|预订|清空|截图|录制|录音|拍|询问|转告|"
            + "起床|休息|运动|锻炼|回家|回来|出门|睡觉|熬夜|迟到|了解|放|弄|"
            + "导出|统计|评估|复核|协助|裁|校(?=好|完|一下)|挂(?=上|好|到|回)|撤(?=掉|下)|留意|冲(?=一冲|洗)|标红|重启|录入|重命名|誊|卸|拖|挪|移(?=到|进|出|上|下|走)|装(?=入|进|上|好|回|滤|配件|支架|门锁|电池|$|[。!])|录(?:下|到|入)|转(?=我|给|一份)|记(?=进|到)|归档|按[\\p{IsHan}]{1,12}?(?=裁|剪|填|贴|标)|开(?=个?发票)|让|盖|监考|作个废|留档|催|称|走"
            + "|" + NAMED_MEETING
            + "|给我(?=一|两|几|[0-9])|(?i:bring)\\s+|到[\\p{IsHan}0-9]{1,12}?(?=拿|领|取|参加)|去[\\p{IsHan}0-9]{1,12}?(?=换|取|拿|办|领)"
            + "|在[\\p{IsHan}]{1,12}?(?:里|上)(?=换|改|录|登记|填)"
            + "|在(?:前台|仓库|库房|窗口|门卫|门岗|服务台|柜台)(?=领|拿|取|交|签)"
            + "|来(?=(?:[\\p{IsHan}0-9]{1,12}?)?(?:取|拿|接|签|交|领))"
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
            + "(?:(?:还)?请(?!问|教|假|勿)(?:你们俩|你俩|你们|你|您|大家|各位)?|麻烦(?:你们俩|你俩|你们|你|您)?|帮我|帮忙|辛苦(?:你|您)?|劳烦(?:你|您)?|拜托(?:你|您)?|有劳(?:你|您)?)"
            + "|能不能|可不可以|能否|可否|是否方便|方不方便|方便的话|方便(?=帮|把|给|回|打|联系|下[来去]|上[来去])|帮(?=把|将)|可以|能|有没有空"
            + "|要不(?:你|您)?(?=把|将|给|帮)|给我(?=回|打|发|转)|需要你|我需要你|我(?:想|要)(?:请|麻烦)(?:你|您)|得(?=[\\p{IsHan}])|必须|待办[:：]?)\\s*");
    private static final Pattern MODIFIER = Pattern.compile(
            "^(?:(?:先|再|然后|接着|另外|还有|顺便|顺手|顺带|顺路|抽个空|尽快|按时|及时|一定要|也要|得要|一起|单独|分别"
            + "|稍微|照样|只管|第一时间|抽空|就(?=把|将|请|麻烦)|都(?=记得|别忘|交|发|填|提交|参加)|线上|线下|去(?=把|将)|改成|改到|改为|需要(?!你)|要(?!不|是|么|求)|给我|帮我|帮(?:个)?忙|只需要|只(?=带|拿|送|交|发)|直接|要不(?=把|将|给)|抽个时间)\\s*)+");
    private static final Pattern MENTION = Pattern.compile("^(?:@[^\\s@,。!?;]{1,40}\\s+)+");
    private static final Pattern RECIPIENT = Pattern.compile("^(?:你们俩|你俩|你们|你|您|大家|各位|咱们|二位|两位|每个人都)\\s*");
    private static final Pattern LIST_MARK = Pattern.compile(
            "^(?:(?:任务|事项)[一二三四五六七八九十0-9]+[:：]|[-*•·☐□]|\\[[ xX]?\\]|\\((?:[0-9]{1,2}|[一二三四五六七八九十]+)\\)"
            + "|[0-9]{1,2}[.)、](?!\\d)|[一二三四五六七八九十]+、)\\s*");
    private static final Pattern REQUEST_PREFACE = Pattern.compile(
            "^(?:紧急[:：]\\s*|对了[、:：]?\\s*|想(?=麻烦|请)|那个[.。…]+\\s*|另外\\s*(?=今|明|后|周|请|麻烦)|(?:往后)?挪到(?=今|明|后|周|星期)|想起来个事\\s*|(?i:pls|please)\\s+(?=把|将|给|帮)|(?:你)?看(?=明|今|后|周)|[\\p{IsHan}]{1,6}(?:老师|师傅|姐|哥|总)[:：]\\s*|看(?=能|可否|方不方便|(?:明|今|后|周|星期)[^,。!?;]{1,24}(?:能不能|可不可以|能否))|请问\\s*(?=能不能|能否|可不可以|可否))");
    private static final Pattern DIRECTED_TOPIC = Pattern.compile(
            "^(?:这份|那份)?(?:校样|资料|文件|表格|附件|材料|合同|清单|报告|照片|[A-Z]{2,8}|[\\p{IsHan}]{0,12}(?:编号|费用|表单|报表|清单|报告|遥控器|配件|证明|户型图|平面图|路线图|纪要|凭证|备案页|回单|日历|收据|流程图|批次表|图片|名单|费|单|附件))\\s*(?=(?:你|您)?(?:能不能|能否|可以|可否|今天|明天|后天|今晚|明早|明晚|(?:下下|下|本|这)?周[一二三四五六日天]|星期))");
    private static final Pattern TASK_TOPIC = Pattern.compile(
            "^(?:(?:这|那|该|最后|第[一二三四五六七八九十0-9]+)[\\p{IsHan}0-9]{1,16}?(?=请|麻烦|拜托|记得)"
            + "|(?:样稿|文件|附件|报价单|报告|报表)(?=发(?:给)?我)|[\\p{IsHan}]{0,12}(?:工单|材料|清单|报表|回执|编号|报告|名单)(?=记得|务必|必须|照样要|还是要)|【[^】]{1,20}】(?=发我|给我|发给我)|[\\p{IsHan}]{0,12}(?:件|表|单|凭据|票据|资料|报告|清单|表格|收据|版本|材料)(?=给我一份|先留一份))");
    private static final Pattern CHAT_TOPIC = Pattern.compile("^小票(?=发(?:给)?我)");
    private static final Pattern ADDRESSED_MOVE = Pattern.compile(
            "^[\\p{IsHan}0-9]{1,24}?(?=(?:你|您)(?:先)?(?:挪|移|搬))");
    private static final String BRING_BACK = "捎[^,。!?;]{1,20}(?:回来|过来)(?:吧|呗|哈|呀)?";
    private static final Pattern TRIP_ACTION = Pattern.compile("^(?:顺走|" + BRING_BACK + "[?]?$)");
    private static final String EVENT_TARGET =
            "(?:(?:到(?:了|达)?|进了|进到)[\\p{IsHan}0-9]{0,14}?(?:站|口|门|室|区|台|场|馆|店|家|公司|仓库|楼下|楼上)"
            + "|刷卡进(?:门|楼)|(?:拿到|收到)[\\p{IsHan}0-9]{1,16}?)";
    private static final String EVENT_REQUEST = "(?:你|您|记得|请|麻烦|帮|通知我|喊我|叫我|给我|发个消息|把|将|拍(?:一?张|照))";
    private static final String UNSCHEDULED_EVENT =
            "(?:洗完[\\p{IsHan}]{1,12}|[\\p{IsHan}]{1,12}换好[\\p{IsHan}]{1,12}|报到)(?:以前|之前|以后|之后|前|后)"
            + "|(?:找到[^,。!?;]{1,20}了|[\\p{IsHan}]{1,10}一到手|(?:你|您)?腾出(?:时间|空)了)(?:就|再)?"
            + "|(?:下回|下次)去[\\p{IsHan}]{1,12}?(?=记得|记着|记住|别忘|请|麻烦)"
            + "|碰到[^,。!?;]{1,20}?(?=帮我|麻烦|请|记得|把)"
            + "|(?:回[\\p{IsHan}]{1,12}?|轮到你[^,。!?;]{1,18}?|碰见[^,。!?;]{1,12}?)(?:的时候|时(?!候))"
            + "|(?:开完[^,。!?;]{1,12}会|签完合同|报名开始前|搬宿舍那天|手上空一点了(?:再)?|下次有机会|这几天空下来)"
            + "|(?:这几天)?(?:你|您)?哪天方便(?:就)?|空了(?=把|帮|找|去)|找个[^,。!?;]{1,12}时段"
            + "|(?:下回|下次)(?:过|路过)[^,。!?;]{1,14}?(?=记得|请|麻烦|把)"
            + "|(?:拿到|等)[^,。!?;]{1,30}?(?:了)?再(?=把|请|记得|去|找|联系|一起|按)"
            + "|(?:[^,。!?;]{1,12}满了的话|[^,。!?;]{1,18}重新[^,。!?;]{1,12}以后)";
    private static final Pattern EVENT_CONTEXT = Pattern.compile(
            "^(?:" + UNSCHEDULED_EVENT + ")\\s*|^(?:(?:等(?:你|您)?)?" + EVENT_TARGET + "(?:以后|之后|后|了)?"
            + "(?=\\s*(?:就?" + EVENT_REQUEST + "|[。!]*$))"
            + "|(?:(?:来|去)?(?:开会|培训|上课|开庭|拿钥匙)|路过[\\p{IsHan}0-9]{0,14}?)(?:的时候|时)"
            + "|(?:回|去)[\\p{IsHan}0-9]{1,12}?(?:途中|路上)"
            + "|(?:跑完步|洗完衣服|做完饭)(?=记得|别忘|请|把)"
            + "|(?:下回|下次|下一次)(?:去[\\p{IsHan}]{1,10}?|拿药|取药|回家(?:路上|途中)?)(?=顺便|顺路|顺手)"
            + "|[\\p{IsHan}]{1,10}?(?:来了|到了)就(?=给|帮|请|把|记得)"
            + "|等[\\p{IsHan}0-9]{1,16}?(?:结束|完成|处理完|散场)(?:以后|之后|后|再)"
            + "|(?:要是|如果)?(?:腾得出手|腾得出空|抽得出空)(?:的时候|的话)?(?=帮|请|麻烦|把)"
            + "|(?:下班)?(?:经过|路过)[\\p{IsHan}]{1,16}?(?=\\s*(?:" + BRING_BACK + "[。!]*$|$))"
            + "|(?:退烧|搬完家|到货|临走|(?:车险|保险|证件|合同)到期)(?:以前|之前|以后|之后|前|后)"
            + "|(?:等(?:收到|拿到|取到)[^,。!?;]{1,24}|等[^,。!?;]{1,24}(?:量了|下来|结束))(?=[,。!?;]|\\s|$)"
            + "|经过[\\p{IsHan}]{1,15}(?:的话|时)(?=$|[。!]|\\s|能|请|帮|把)"
            + "|(?:这两天)?(?:空了|找个(?:空档|空闲时间|空儿)|抽空|有空)(?=找|给|把|帮|教|去))"
            + "(?:就(?=" + EVENT_REQUEST + "))?\\s*");
    private static final Pattern RELAY_OR_CALL = Pattern.compile(
            "^(?:跟|和|向|给)[\\p{IsHan}0-9]{1,14}?(?:说一声|打个招呼|(?:回|打)(?:一下|个)?电话|报(?:一下|个|上)|问(?:一下|下))"
            + "|^(?:回|打)(?:一下|个)?(?:[\\p{IsHan}0-9]{1,14}的)?电话|^给我(?:回|来|打)?(?:个)?电话");
    private static final String CONDITION_ACTION = "(?=请|麻烦|记得|别忘|顺手|顺便|把|将)";
    private static final Pattern CONDITION = Pattern.compile(
            "^(?:(?:如果|要是|假如|万一|一旦|遇到|等|用完).{1,40}?(?:就\\s*|" + CONDITION_ACTION + ")"
            + "|(?:收到|拿到|取到|签收|[^,。!?;]{1,16}没).{1,20}?(?:以后|之后|前|后)\\s*" + CONDITION_ACTION + ")");
    private static final Pattern DIRECT_REQUEST = Pattern.compile(
            "^(?:退我|发(?:给)?我|回(?:复)?我|转(?:发|给)我|通知我|联系我|告诉我|叫我|拍给我|私聊我|喊我|给我(?:一|两|几|[0-9]|[^,。!?;\\n]{1,18}(?:表|稿|件|材料|凭证)|回(?:个|一下)?|[^,。!?;\\n]{1,20}(?:号码|手机号|电话号|地址|收件信息))|过一下|过一眼)");
    private static final Pattern CHAT_IMPERATIVE = Pattern.compile("(?:一下(?:吧|哈|呀)?|吧)\\s*$");
    private static final Pattern RELATIVE = Pattern.compile(
            "大后天|后天|明天|明儿|明早|明晚|今晨|今天|今晚|今早");
    private static final Pattern CHINESE_DATE = Pattern.compile(
            "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})(?:日|号)?(?!\\d)");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)");
    private static final String DAY_NUMBER = "(?:0?[1-9]|[12][0-9]|3[01]|[一二两三四五六七八九]|[一二三]?十[一二三四五六七八九]?)";
    private static final Pattern DAY_OF_MONTH = Pattern.compile(
            "(?<![\\d一二两三四五六七八九十第])(" + DAY_NUMBER + ")\\s*[号日](?!\\s*(?:楼|栋|幢|座|线|站|室|床|桌|车|柜|箱|门|口|版|件|票|文件|订单|任务|样品|报告))");
    private static final Pattern SCOPED_MONTH_DAY = Pattern.compile(
            "(本|这|下)(?:个)?月(" + DAY_NUMBER + ")[号日](?!\\s*[楼栋线室柜])");
    private static final Pattern SCOPED_YEAR_DATE = Pattern.compile(
            "(今|明|后)年(" + DAY_NUMBER + ")月(" + DAY_NUMBER + ")[日号]");
    private static final Pattern SHORT_DATE = Pattern.compile("(?<![\\d/.])(\\d{1,2})(?:/|[.-](?=\\d{1,2}\\s*(?:上午|下午|傍晚|早上|中午|晚上|凌晨|[0-9]{1,2}:[0-9]{2})))(\\d{1,2})(?![\\d/.]|\\s*(?:英寸|公分|厘米|毫米|寸|份|件|米|元|公斤|斤|成|倍|名|人|页|张))");
    private static final Pattern FULL_CHINESE_DATE = Pattern.compile("\\d{4}年\\d{1,2}月\\d{1,2}[日号]");
    private static final Pattern WEEKDAY = Pattern.compile("(?:每(?:个)?|(?:(本|这|下下|下|上)(?:个)?)?)(?:周|星期|礼拜)([一二三四五六日天1-7])");
    private static final Pattern LAST_WORKDAY = Pattern.compile("(?:本|这|下)(?:个)?月最后(?:一|1)个工作日");
    private static final Pattern MONTH_END = Pattern.compile("(?:(?:本|这|下)(?:个)?月(?:底|末|最后一天)|月底|月末)");
    private static final Pattern YEAR_END = Pattern.compile("(?:今年|本年)?(?:年底|年末)|(?:今年|本年)(?:的)?最后(?:一|1)天");
    private static final String AMOUNT = "(?:[0-9]{1,4}|[零〇一二两三四五六七八九十]{1,3})";
    private static final String DURATION_VALUE = "(半|" + AMOUNT + ")(?:个)?(半)?(分钟|刻钟|小时|钟头|天|周)(半)?"
            + "(?:(" + AMOUNT + ")分钟)?";
    private static final Pattern DURATION = Pattern.compile(
            "(?<![\\d零〇一二两三四五六七八九十])(?:过)?" + DURATION_VALUE + "(?:以后|之后|之内|以内|后|内)");
    private static final Pattern AFTER_DURATION = Pattern.compile(
            "(?:再)?(?:过|等)" + DURATION_VALUE + "(?=\\s*(?:[,，。!?]|$|提醒我|记得|请|再|叫我|通知我|把|将|" + VERBS + "))");
    private static final Pattern DEADLINE = Pattern.compile("(?:最晚(?:到|在)?|最迟(?:到|在)?|截止(?:到|至)?|截至|不晚于)\\s*[:：]?\\s*");
    private static final Pattern RESCHEDULE = Pattern.compile(
            "^(?:(?:时间|现在|现|店里|对方|师傅|客服|负责人)\\s*改(?:到|为|成)|改(?:到|为|成)|应该是|改(?=今|明|后|周|星期))\\s*[:：]?\\s*");
    private static final Pattern APPOINTMENT_SHIFT = Pattern.compile(
            "^(?:[^,。!?;]{0,28}(?:预约|约定|时间)|[^,。!?;]{1,12}(?:刚说|刚通知))(?:往后)?(?:推到|改到|改成|挪到|改(?=今|明|后|周|星期|下周))");
    private static final Pattern DEFER_REQUEST = Pattern.compile("^(?:先|暂时)?(?:别|不要|不用)(?:再)?\\s*");
    private static final Pattern APPOINTMENT_TIME = Pattern.compile("^(?:预约|约)(?:时间)?(?:在|是|定在)\\s*");
    private static final Pattern EVENT_USE = Pattern.compile(
            "^(?:(?:开会|碰头|培训|上课|交接)(?:的时候|时)要用|(?:办手续|办业务|登记|出发)(?:之前|前)要用"
            + "|我(?:得|要)(?:骑|开|用))[。!]*$");
    private static final Pattern TRAILING_PERIOD = Pattern.compile("^(上午|下午|中午|早上|晚上)(?:的)?(?:哦|哈|啊|呀)?$");
    private static final Pattern DEFER_DISPOSAL = Pattern.compile("^(?:把|将)[^,。!?;]{1,24}?(" + VERBS + ")");
    private static final Pattern KEEP_FOR_LATER = Pattern.compile(
            "^[\\p{IsHan}]{0,10}(?:小票|发票|收据|凭证|保修单|包装|原件)(?:先(?:别|不要)(?:扔|丢|处理)|先收好)(?:了)?$");
    private static final Pattern FUTURE_USE = Pattern.compile(
            "^(?:(?:下周|下个月|下次|过几天|回头|以后).{0,20}(?:要用|换货|报销|核对|退货)"
            + "|[^,。!?;]{0,14}(?:查账|开[^,。!?;]{1,12}证明)(?:的时候|时)(?:还)?(?:要看|得交))");
    private static final Pattern REPORTED_DUTY = Pattern.compile("^[\\p{IsHan}]{1,12}?(?:说|通知说)\\s*");
    private static final Pattern TAKE_OVER = Pattern.compile(
            "^(?:你|您)?(?:帮我|替我|麻烦你)(提交|交|发|寄|送|取|领|拿|办|付)(?:一下|一趟)?(?:吧|哈)?[。!]*$");
    private static final Pattern REQUIRED_ACTION = Pattern.compile("^(?:要|得|需要|必须)");
    private static final Pattern FLEXIBLE_TIMING = Pattern.compile(
            "(?:^|[,。!?;\\n])\\s*(?:(?:等|如果|要是|一旦)[^,。!?;\\n]{1,30}(?:到货|送到|到了|结束|完成|处理完)了?"
            + "(?=[,。!?;\\n]|就|请|麻烦|记得|别忘)|下班(?:以后|之后|后)|回头|离下次[^,。!?;]{1,12}还有(?:一阵子|阵子|一段时间)"
            + "|有空(?:的话|了|时)?(?=[,。!?;\\n]|$|就|再|帮|请|麻烦|弄|去|记得)|不急(?=[,。!?;\\n]|$)"
            + "|晚饭前|抽空|下回|下次|下一次|回家路上|万一|跑完步|洗完衣服"
            + "|(?:收到|拿到|取到|退烧|搬完家|到货|临走|(?:车险|保险|证件|合同)到期)[^,。!?;\\n]{0,18}(?:以前|之前|以后|之后|前|后)"
            + "|(?:等|如果|要是)[^,。!?;\\n]{1,35}(?=[,。!?;\\n]|$)"
            + "|(?:这两天)?(?:空了|找个(?:空档|空闲时间|空儿))|(?:周末)?有空|哪天|下班(?:经过|路过)"
            + "|[\\p{IsHan}]{1,10}?(?:来了|到了)就|" + UNSCHEDULED_EVENT + ")|(?:报销|办手续)(?:的时候|时)(?:我)?(?:得|要)用");
    private static final Pattern ORIGINAL_SCHEDULE = Pattern.compile("^(?:原定|原计划)");
    private static final Pattern WITHDRAWAL = Pattern.compile(
            "^(?:(?:先|暂时|那就|等等)?(?:不用了|不必了|算了|算啦|算咯)|(?:刚通知|刚接到通知)?(?:会议|会)取消了|(?:会议|培训|活动)改到待定)$");
    private static final Pattern MEETING_ACTION = Pattern.compile(
            "(?:开会|参会|" + NAMED_MEETING + ")(?=$|[。!\\s])|(?:参加|召开|主持|出席).{0,10}会议");
    private static final Pattern INFRA_NOTICE = Pattern.compile("^(?:临时|计划)?停[水电气暖](?:了)?[。!]*$");
    private static final Pattern WITHDRAWAL_ACTION = Pattern.compile(
            "^(?:([\\p{IsHan}0-9]{1,16}?)(?=先|暂时|就))?(?:先|暂时|就)?(?:别|不要|不用)(?:再)?([\\p{IsHan}]{1,12}?)(?:了)?$");
    private static final Pattern ALTERNATIVE_QUESTION = Pattern.compile("还是(?:我|你|他|她|我们|你们)(?:来|去|做|发|买|处理)");
    private static final Pattern LOCAL_CANCEL = Pattern.compile(
            "(?:(?:这|的)(?:件事|事|条)(?:先放一放|先搁一搁|先搁着|先撤回|先别做|先不提了|撤了|作废|我自己来)|已经交过了?(?:不用再交)?|这单(?:已|已经)签收了|这(?:件事|事)(?:已经|刚才)(?:找[^,。!?;]{1,12})?(?:弄完|办完|处理完|做好)了?|(?:的)?安排作废|这个[^,。!?;]{0,6}(?:已|已经)结项了)"
            + "(?:哈|啦|啊)?$");
    private static final Pattern EDITING_COMMENT = Pattern.compile(
            "^这(?:句话|句|几个字)(?:(?:我)?(?:只是|就是)(?:给你)?(?:演示|测试|练习)|怎么翻译|怎么念|是例句|是测试|怎么理解)");
    private static final Pattern FOLLOWUP_FILLER = Pattern.compile(
            "^(?:(?:哦|噢|啊|诶)?(?:等下|等一下|等会儿|等等)|噢|哦|哦不|哦对|啊|对了|忘了告诉你|我说岔了|补一句(?:哈|啊)?|不对|说错了|我说错了)$");
    private static final Pattern ACTIVE_SCENE = Pattern.compile(
            "^(?:(?:我(?:们)?(?:现在)?)?(?:正在|正)(?:投(?:着)?屏|直播|广播|演示|开(?:着)?车|等你付款)|我在广播|气球要飞了|(?:我|你)(?:现在|这会儿)手上(?:还)?有(?:胶水|油|水|面粉|泥)|投影(?:刚|已经)亮了|离(?:红绿灯|路口|收费站)还有[0-9一二两三四五六七八九十]{1,5}秒)");
    private static final Pattern IMMEDIATE_POSITION = Pattern.compile("^(?:我|我们)(?:现在|正)站在[^,。!?;]{1,20}(?:前|旁边|边上)$");
    private static final Pattern CORRECTED_REQUEST = Pattern.compile("^(?:是)?(?=今|明|后|周|下周|下下周|本周|这周|星期|礼拜)");
    private static final Pattern TALK_ABOUT_SCHEDULE = Pattern.compile("^先(?:把时间改一下|改一下时间)$");
    private static final Pattern TIME_CUTOFF_REASON = Pattern.compile("^不用(?:麻烦)?(?:你|您)?(?:跑两趟|来两趟|特地跑一趟)$");
    private static final Pattern INSTANT_EVENT = Pattern.compile(
            "^等(?:[^,。!?;]{0,16})(?:滴|响|叫)一声(?:就|再)(?:把|拔|关|按|拿)");
    private static final Pattern COMPLETED_FOR_USER = Pattern.compile(
            "^(?:(?:我)?(?:今天|上午|下午|刚才)?(?:已经)?(?:帮你|替你)|([^,。!?;]{1,14})我(?:刚|已经))"
            + "(领|拿|交|发|寄|办|填|签|修)(?:回来|过|完|好)?了?$");
    private static final Pattern NOTICE_ONLY = Pattern.compile(
            "^(?:(?:[^,。!?;]{0,14})道路封闭|门禁升级|[^,。!?;]{0,12}关店是营业时间说明"
            + "|[^,。!?;]{1,26}的人是(?:行政|财务|司机|值班员|他们))(?=[,。!?;]|$)");
    private static final Pattern EVENT_NOT_DEADLINE = Pattern.compile(
            "^(?:看见|等到|收到)[^,。!?;]{1,28}(?:以后|之后|后)再");
    private static final Pattern INLINE_SHIFT = Pattern.compile(
            "^([^,。!?;]{1,20}?)(?:从|由)([^,。!?;]{1,24}?)(?:改到|改成|挪到|推到)([^,。!?;]{1,28})$");
    private static final Pattern SHIFT_TOPIC = Pattern.compile(
            "^[^,。!?;]{1,24}(?:的交货时间顺延|原来是[^,。!?;]{1,22}领)");
    private static final Pattern STATUS_QUESTION = Pattern.compile(
            "(?:了(?!解)|过(?!来|去)|完成).{0,20}(?:没有|没|吗|么|了吧)\\s*[?。]?\\s*$");
    private static final Pattern END_PARTICLE = Pattern.compile("了\\s*[!。]?\\s*$");
    private static final Pattern QUESTION_END = Pattern.compile("(?:吗|么|没有|没|呢|[?])\\s*[。]?\\s*$");
    private static final Pattern CANCELLATION_NOTICE = Pattern.compile(
            "^(?:取消(?:了|吧|$)|改天(?:吧|再说|再议)|(?:先|暂时)?(?:算了|算啦|算咯|作废|不用了|不必了|无需了))");
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
            + "|说明(?:网络|系统|服务)|的(?:速度|样子|声音)|(?:临时|计划)?停[水电气暖](?:了)?$|是(?:他|她|我)(?:自己)?的计划");
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
    private static final Pattern PHYSICAL_ACTION = Pattern.compile(
            "^(?:(?:来楼下|来门口)(?:拿|接|取)|(?:把[^,。!?;]{1,24}?)?(?:看一下|递|扶|按住|举着|拔|(?:打|关)开|关(?:灯|门|窗|火|(?=一下|了|掉|好|上|闭))))");
    private static final Pattern LIVE_CUE = Pattern.compile(
            "^(?:我(?:这边|这里|这儿|这会儿|现在)?(?:看不清|够不着|看不到|腾不开手)|我(?:正好|正要|马上|这就)(?:要)?(?:下楼|上楼|过去|过来)"
            + "|(?:他|她|对方)(?:就|正)?在(?:你|您|我)(?:旁边|边上|面前)(?:等|站|看)"
            + "|锅(?:还|正)在响|锅(?:里)?(?:已经|正|还)?(?:在)?冒烟|我(?:现在)?手上(?:全|都)是(?:水|油|面粉|泥)|我现在要进[^,。!?;]{1,8}(?:区|室|门)|我(?:马上|这就)(?:装回|接回)|我(?:还|正)在(?:前台|门口|楼下)等你)");
    private static final Pattern QUICK_FIX = Pattern.compile(
            "^(?:补|填|写|签)(?:一)?个(?:名字|姓名|字)就(?:行|好|可以)(?:了)?[。!]*$");
    private static final Pattern SELF_OFFER = Pattern.compile(
            "^(?:(?:我)?(?:可以|能|愿意)(?:先)?(?:借给你|替你|帮你|代你)"
            + "|(?:拿到|收到|取到|弄好|办好)了?(?:以后|之后|后)?(?:我再?|就)?给你)");
    private static final Pattern LOCAL_OPINION = Pattern.compile("^(?:给我)?看看(?:这|那)(?:个)?(?:颜色|色号|色调)");
    private static final Pattern OPINION_QUESTION = Pattern.compile("^是不是(?:有点|太|很)?(?:偏|深|浅|灰|亮|暗)");
    private static final String HOUR = "(?:[0-9]{1,2}|[零〇一二两三四五六七八九十]{1,3})";
    private static final String CLOCK_OFFSET = "(?:一刻|半小时|" + HOUR + "分钟)";
    private static final Pattern TIME = Pattern.compile(
            "(凌晨|清晨|早晨|早上|上午|中午|午后|下午|傍晚|晚上|晚间|夜里|早|晚)?\\s*(?<!\\d)("
                    + HOUR + ")(?::([0-9]{2})(?![0-9])|[点时](?:(半|一刻|三刻)|("
                    + HOUR + ")分|([0-9]{2}|[零〇][一二三四五六七八九]|[一二三四五]?十[一二三四五六七八九]?)"
                    + "(?![0-9零〇一二两三四五六七八九十号楼栋座线份件个元米]))?(?:整(?!理|改|修|顿|合)|钟(?!头))?(?:差(" + CLOCK_OFFSET + "))?)");
    private static final String TIME_RANGE = TIME.pattern() + "\\s*(?:到|至|[-~])\\s*" + TIME.pattern();
    private static final String CALENDAR_DATE =
            LAST_WORKDAY.pattern() + "|" + SCOPED_YEAR_DATE.pattern() + "|" + SCOPED_MONTH_DAY.pattern() + "|" + NUMERIC_DATE.pattern()
            + "|" + CHINESE_DATE.pattern() + "|" + SHORT_DATE.pattern() + "|" + RELATIVE.pattern()
            + "|" + WEEKDAY.pattern() + "|" + MONTH_END.pattern() + "|" + YEAR_END.pattern()
            + "|" + DAY_OF_MONTH.pattern();
    private static final Pattern DATE_REFERENCE = Pattern.compile(
            "(?:(?:" + CALENDAR_DATE + ")(?:\\s*(?:" + TIME.pattern() + "|上午|下午|早上|晚上))?"
            + "|" + TIME.pattern() + ")\\s*(?:会议|沟通|通话|录音)?的"
            + "|" + NUMERIC_DATE.pattern() + "\\s*(?:版本|版(?!式|面|权|图))");
    private static final String DATE_RANGE = "(?:" + CALENDAR_DATE + ")\\s*(?:到|至)\\s*(?:" + CALENDAR_DATE + ")";
    private static final Pattern CONTEXT = Pattern.compile(
            "^(?:(?:(?:在|于|从|到|(?:先)?定在|约在)?(?:" + DATE_RANGE + "|" + CALENDAR_DATE + "|" + DURATION.pattern() + "|" + AFTER_DURATION.pattern()
            + "|" + TIME_RANGE + "|" + TIME.pattern() + ")"
            + "|下班|回家|回来|出门|有空|回头|到家|到公司|周末|假期|到时候|过会儿?|待会儿?|等会儿?|一会儿?|稍后"
            + "|清晨|早晨|早上|上午|中午|午后|下午|傍晚|晚上|下周(?![一二三四五六日天1-7]))(?:的话|之前|以前|以后|之后|的时候|时候|路上|结束之前|之间|开始|前后|前|后)?(?:左右|前后)?(?:吧(?=\\s*$))?\\s*"
            + "|(?:在|于)?(?:睡觉|吃饭|饭|午饭|午休|晚饭|早餐|开会|开完会|会|封箱|上班|放学|起床|起来)(?:之前|以后|之后|的时候|时候|前|后)\\s*)+");
    private static final Pattern SCHEDULED_VISIT = Pattern.compile(
            "^[\\p{IsHan}]{1,12}?(?:" + CALENDAR_DATE + "|" + TIME.pattern() + "|上午|下午|中午|晚上|晚点)"
            + "(?:上午|下午|中午|晚上|" + TIME.pattern() + ")?(?:过来|来|到)[\\p{IsHan}]{0,12}$");
    private static final Pattern SENTENCE_END = Pattern.compile("[。!]+\\s*$");
    private static final Pattern EARLY = Pattern.compile("明早|今早|今晨|清晨|早晨|早上|上午");
    private static final Pattern TASK_BOUNDARY = Pattern.compile("[;；\\r\\n]+|(?<=。)(?=\\s*[^。\\r\\n])");
    private static final Pattern ENUMERATED_BOUNDARY = Pattern.compile("[,，;；。\\r\\n]+");
    private static final Pattern TASK_ENUMERATION = Pattern.compile("^\\s*(?:两|三|[2-9])件事\\s*[:：,，]\\s*");
    private static final Pattern MINUTES_TO_HOUR = Pattern.compile("差(" + CLOCK_OFFSET + ")(?:钟)?\\s*(" + HOUR + "[点时](?:半|一刻|三刻)?)");
    private static final Pattern MINUTES_PAST_HOUR = Pattern.compile("([点时])(?:刚过|过|又)(?=" + HOUR + "分)");
    private static final Pattern EVENING_CLOCK = Pattern.compile("([今明]晚)(?:过(?=" + HOUR + "[点时:])|凌晨(?=[零〇0][点时:]))");
    private static final Pattern BEFORE_HOUR_WORDING = Pattern.compile("(一刻|半小时)不到(?=" + HOUR + "[点时])");
    private static final Pattern MESSAGE_DECORATION = Pattern.compile(
            "^(?:(?:[🌟🚩❗‼️✨⭐]+|【(?:提醒|待办|重点)】|(?i:todo)[:：]|就是(?=今|明|后|周|星期|下周)|(?:重点|呃|嗯|姐|哥|(?i:hi))[~～—❗️\\s]+)\\s*)+");
    private static final Pattern DICTATION_SPACES = Pattern.compile(
            "(?<=\\p{IsHan})[ \\t]+(?=[\\p{IsHan}0-9])|(?<=[0-9])[ \\t]+(?=\\p{IsHan})");
    private static final Pattern CLOCK_WITH_PARTICLE = Pattern.compile("([点分])(?:啊|呀|哈)$");
    private static final Pattern TIME_TOPIC_BOX = Pattern.compile("^\\(([^()]{1,40})\\)(?=\\s*(?:把|将|请|记得|麻烦))");
    private static final Pattern DATE_PARTICLE = Pattern.compile(
            "([号日天]|[天周]后)的(?=(?:清晨|早上|上午|中午|下午|傍晚|晚上|凌晨)|" + HOUR + "[点时:])");
    private static final Pattern LEADING_DATE = Pattern.compile("^(?:在|于|从|到)?(" + CALENDAR_DATE + ")");
    private static final Pattern SAME_DAY_CLOCK = Pattern.compile("^(?:上午|中午|下午|傍晚|晚上)(?=\\s*" + HOUR + "[点时:])");
    private static final Pattern COURTESY_QUESTION = Pattern.compile("(?:行吗|成吗|成不|可以吗|可以不|方便不|行不行)[?。]?$");
    private static final Pattern NOON = Pattern.compile("中午");
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

    static List<String> independentlyScheduledTasks(String message) {
        if (!stripQuotedText(message).equals(message)) return List.of(message);
        Matcher enumeration = TASK_ENUMERATION.matcher(message);
        boolean enumerated = enumeration.find();
        List<String> parts = (enumerated ? ENUMERATED_BOUNDARY.splitAsStream(message.substring(enumeration.end())) : TASK_BOUNDARY.splitAsStream(message))
                .map(String::strip).toList();
        if (parts.size() < 2) return sameDayTasks(message);
        if (enumerated && parts.size() == 2) {
            List<String> inherited = sameDayTasks(String.join(",", parts));
            if (inherited.size() == 2) return inherited;
        }
        for (String part : parts) {
            // A bare clock in a later clause may still refer to the preceding clause's date.
            String normalized = LIST_MARK.matcher(normalize(part)).replaceFirst("");
            if ((!hasDate(normalized) && durationIn(normalized) == null) || stripContext(normalized).equals(normalized)
                    || RESCHEDULE.matcher(normalized).find() || LOCAL_CANCEL.matcher(normalized).find()
                    || notificationTask(part) == null) {
                return List.of(message);
            }
        }
        return parts;
    }

    private static List<String> sameDayTasks(String message) {
        String[] parts = message.split("[,，]", -1);
        if (parts.length != 2) return List.of(message);
        String first = normalize(parts[0]);
        String second = normalize(parts[1]);
        Matcher day = LEADING_DATE.matcher(first);
        if (!day.find() || !SAME_DAY_CLOCK.matcher(second).find() || hasDate(second) || !hasSchedule(second)
                || notificationTask(first) == null || withdraws(first, second)
                || !rescheduledTime(second).isEmpty()) return List.of(message);
        String date = switch (day.group(1)) {
            case "明早", "明晚", "明儿" -> "明天";
            case "今早", "今晨", "今晚" -> "今天";
            default -> day.group(1);
        };
        String inherited = date + parts[1].strip();
        return notificationTask(inherited) == null ? List.of(message) : List.of(parts[0].strip(), inherited);
    }

    static String afterForwardedBlock(String message) {
        return FORWARDED_BLOCK.matcher(message).replaceFirst("").stripLeading();
    }

    static boolean needsTimingConfirmation(String message, Match match) {
        String text = MENTION.matcher(stripQuotedText(normalize(message))).replaceFirst("");
        text = RECIPIENT.matcher(text).replaceFirst("");
        if (!hasSchedule(match.trailingTime())) {
            if (EVENT_NOT_DEADLINE.matcher(stripContext(text)).find()) return true;
            String afterEvent = EVENT_CONTEXT.matcher(text).replaceFirst("");
            if (!afterEvent.equals(text) && !hasSchedule(afterEvent)) return true;
        }
        if (!TIME.matcher(match.clause()).find() && !hasSchedule(match.trailingTime())
                && text.matches("^[^,。!?;]{0,16}哪天方便(?:就)?[^。!?;]+[。!]*$")) return true;
        return !hasSchedule(match.clause()) && !hasSchedule(match.trailingTime())
                && (FLEXIBLE_TIMING.matcher(text).find() || text.contains("下次") && text.startsWith("请你留意"));
    }

    static String classificationText(String message) {
        Match match = assess(message, true).match();
        return match == null ? stripQuotedText(normalize(message)) : match.clause();
    }

    private static Assessment assess(String message, boolean chat) {
        String original = normalize(message);
        String unquoted = stripQuotedText(original);
        Match english = EnglishTaskRules.match(unquoted);
        if (english != null) return new Assessment(english, false);
        boolean blocked = !unquoted.equals(original);
        String deferredAction = "";
        String carriedSchedule = "";
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
            String carry = carriedSchedule;
            carriedSchedule = "";
            boolean onSite = false;
            String[] parts = sentence.split("[,，]");
            for (int index = 0; index < parts.length; index++) {
                String item = LIST_MARK.matcher(parts[index].strip()).replaceFirst("");
                String clause = MENTION.matcher(item).replaceFirst("");
                if (clause.isEmpty()) {
                    continue;
                }
                String following = followingClause(parts, index, sentences, sentenceIndex);
                String nextClause = index + 2 < parts.length ? stripSentenceEnd(parts[index + 2].strip()) : "";
                if (WITHDRAWAL.matcher(following).matches() && WITHDRAWAL_ACTION.matcher(nextClause).matches()
                        && !withdraws(clause, nextClause)) following = nextClause;
                if (TALK_ABOUT_SCHEDULE.matcher(stripSentenceEnd(clause)).matches() && hasSchedule(following)) continue;
                if (LOCAL_CANCEL.matcher(stripSentenceEnd(clause)).find()) {
                    blocked = true;
                    carry = "";
                    continue;
                }
                String rescheduled = rescheduledTime(following);
                if (index > 0 && stripSentenceEnd(parts[index - 1].strip()).matches("(?:我跟你)?说错了|我说岔了|不是[^,。!?;]{1,15}")) {
                    clause = CORRECTED_REQUEST.matcher(clause).replaceFirst("");
                }
                String combined = (carry + REQUEST_PREFACE.matcher(clause).replaceFirst("")).stripLeading();
                combined = DIRECTED_TOPIC.matcher(combined).replaceFirst("");
                Matcher inlineShift = INLINE_SHIFT.matcher(stripSentenceEnd(combined));
                if (inlineShift.matches() && hasSchedule(inlineShift.group(2)) && stripContext(inlineShift.group(2)).isEmpty()) {
                    String newTimeAndAction = inlineShift.group(3);
                    String action = stripContext(newTimeAndAction);
                    String schedule = newTimeAndAction.substring(0, newTimeAndAction.length() - action.length()).strip();
                    if (hasSchedule(schedule) && isTaskClause(action, true, true, chat, true)) {
                        if (!withdraws(combined, following)) return new Assessment(new Match(combined, schedule), false);
                        blocked = true;
                        carry = "";
                        continue;
                    }
                }
                if (!rescheduled.isEmpty() && SHIFT_TOPIC.matcher(combined).find()) {
                    if (!withdraws(combined, nextClause)) return new Assessment(new Match(combined, rescheduled), false);
                    blocked = true;
                    carry = "";
                    continue;
                }
                Match deferred = deferredTask(deferredAction, combined);
                deferredAction = "";
                if (deferred != null) {
                    if (!withdraws(deferred.clause(), following)) return new Assessment(deferred, false);
                    blocked = true;
                    carry = "";
                    continue;
                }
                if (!rescheduled.isEmpty()) {
                    combined = ORIGINAL_SCHEDULE.matcher(combined).replaceFirst("");
                }
                Matcher shifted = APPOINTMENT_SHIFT.matcher(combined);
                if (shifted.find()) {
                    String schedule = stripSentenceEnd(combined.substring(shifted.end()));
                    if (hasSchedule(schedule) && stripContext(schedule).isEmpty()) {
                        carry = schedule + " ";
                        continue;
                    }
                }
                String withoutTime = stripContext(combined);
                String withoutRecipient = RECIPIENT.matcher(withoutTime).replaceFirst("");
                String withCondition = stripContext(withoutRecipient);
                Matcher deferral = DEFER_REQUEST.matcher(withCondition);
                if (deferral.find() && (RESCHEDULE.matcher(following).find()
                        || (hasSchedule(following) && stripContext(following).startsWith("再")))) {
                    deferredAction = stripSentenceEnd(withCondition.substring(deferral.end()));
                }
                String afterEvent = EVENT_CONTEXT.matcher(withCondition).replaceFirst("");
                boolean followUp = !afterEvent.equals(withCondition);
                if (followUp) {
                    afterEvent = RECIPIENT.matcher(afterEvent).replaceFirst("");
                }
                String unconditioned = CONDITION.matcher(afterEvent).replaceFirst("");
                String body = TASK_TOPIC.matcher(DIRECTED_TOPIC.matcher(unconditioned).replaceFirst("")).replaceFirst("");
                Matcher newTime = RESCHEDULE.matcher(body);
                boolean rescheduling = newTime.find();
                if (rescheduling) body = body.substring(newTime.end());
                if (chat) {
                    body = CHAT_TOPIC.matcher(body).replaceFirst("");
                }
                boolean addressed = !withoutRecipient.equals(withoutTime) || !item.equals(clause);
                boolean timed = !withoutTime.equals(combined) || !withCondition.equals(withoutRecipient)
                        || (rescheduling && !stripContext(body).equals(body));
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
                if (TIME_CUTOFF_REASON.matcher(stripSentenceEnd(body)).matches() && timed) {
                    carry = combined.substring(0, combined.length() - withoutTime.length()).strip() + " ";
                    continue;
                }
                if (body.isEmpty() || isInstructionLead(body)
                        || (timed && CLOCK_WITH_PARTICLE.matcher(combined).find()
                        && stripContext(CLOCK_WITH_PARTICLE.matcher(combined).replaceFirst("$1")).isEmpty())) {
                    combined = CLOCK_WITH_PARTICLE.matcher(combined).replaceFirst("$1");
                    carry = combined.replaceFirst("(?<=[前后点分])吧$", "") + " ";
                    if (index + 1 == parts.length && sentence.endsWith("\n") && hasSchedule(carry)) carriedSchedule = carry;
                    continue;
                }
                if (SELF_OFFER.matcher(withCondition).lookingAt() || EDITING_COMMENT.matcher(following).find()
                        || NOTICE_ONLY.matcher(body).find() || stripSentenceEnd(body).matches("^[^,。!?;]{1,28}先不用(?:了)?$")
                        || (!hasSchedule(combined) && (body.matches("^(?:能)?(?:先)?回(?:个|一下)?收到(?:吗)?[?。!]*$")
                        && following.matches("^我(?:在|正|等你)等?(?:着)?$") || body.matches("^先帮忙确认[^,。!?;]{0,18}(?:收没收到|有没有收到|收到没)[?。!]*$")))
                        || body.matches("^(?:看|看看)我记得(?:没错|对不对)(?:吧|吗|没)?[?。!]*$")
                        || body.matches("^看(?:一下|一眼)[^,。!?;]{1,40}(?:是不是|有没有|有没)[^,。!?;]{0,18}[?。!]?$")
                        && following.matches("^我[^,。!?;]{0,12}(?:看不到|看不见|够不着|伸不到)$")
                        || INSTANT_EVENT.matcher(withCondition).find()
                        || (!hasSchedule(combined) && (ACTIVE_SCENE.matcher(MENTION.matcher(previous).replaceFirst("")).find()
                        || (combined.matches("^(?:等会儿?|过会儿?|一会儿?).*") && IMMEDIATE_POSITION.matcher(following).matches()
                        && PHYSICAL_ACTION.matcher(REQUEST.matcher(body).replaceFirst("")).find())))
                        || isImmediateOrOffer(body, timed || followUp, onSite, following) || withdraws(combined, following)) {
                    blocked = true;
                    carry = "";
                    continue;
                }
                if (chat && KEEP_FOR_LATER.matcher(stripSentenceEnd(body)).matches()
                        && (hasDate(following) || FUTURE_USE.matcher(following).find())) {
                    return new Assessment(new Match(combined, ""), false);
                }
                String timingLead = combined.substring(0, combined.length() - withoutTime.length()).strip();
                boolean imperativeDisposal = timed && (item.startsWith("@所有人") || timingLead.endsWith("前")
                        || timingLead.endsWith("内") || timingLead.matches("^(?:明[天早晚儿]|大?后天).*")
                        || DEADLINE.matcher(following).lookingAt());
                boolean keepForUse = chat && body.matches("^收好(?:哈|吧|啊|哦)?[。!]*$")
                        && following.matches("^(?:办手续|办业务|报销|登记)(?:时|的时候)?(?:需要|得要|要)(?:用)?原件$");
                boolean chatFollowUp = chat && followUp && body.matches(
                        "^(?:再)?(?:找[^,。!?;]{1,15}把|给[^,。!?;]{1,15}换|去[^,。!?;]{1,12}?把)[^,。!?;]{1,32}[。!]*$");
                if (keepForUse || chatFollowUp || isTaskClause(body, timed || followUp || DEADLINE.matcher(following).lookingAt(),
                        addressed || !afterEvent.equals(unconditioned), chat, imperativeDisposal)) {
                    String trailing = rescheduled;
                    if (trailing.isEmpty()) {
                        trailing = trailingTime(parts, index, following, combined);
                    }
                    if (trailing.isEmpty() && !hasSchedule(combined)) {
                        trailing = reportedDeadline(previous, combined);
                        String action = stripSentenceEnd(body);
                        if (trailing.isEmpty() && action.matches("(?:把|将)?[^,。!?;]{0,20}带上(?:哈|吧|啊|哦)?")) {
                            String rest = stripContext(previous);
                            if (rest.matches("游泳|上课|培训|训练|集合|开会|出门") && hasSchedule(previous)) {
                                trailing = previous.substring(0, previous.length() - rest.length()).strip();
                            }
                        }
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

    private static boolean isImmediateOrOffer(String body, boolean followUp, boolean onSite, String following) {
        Matcher request = REQUEST.matcher(body);
        String action = request.find() ? body.substring(request.end()) : body;
        if (SELF_OFFER.matcher(body).lookingAt()) {
            return true;
        }
        if (REMINDER.matcher(body).lookingAt() || body.startsWith("待办")) {
            return false;
        }
        return (LOCAL_OPINION.matcher(body).find() && OPINION_QUESTION.matcher(following).find())
                || IMMEDIATE.matcher(body).lookingAt() || IMMEDIATE.matcher(action).lookingAt()
                || IMMEDIATE_DISPOSAL.matcher(action).lookingAt()
                || (!followUp && (QUICK_FIX.matcher(action).matches() || (onSite && LIVE_HANDOFF.matcher(action).lookingAt())
                || (LIVE_CUE.matcher(following).find() && PHYSICAL_ACTION.matcher(MODIFIER.matcher(action).replaceFirst("")).find())));
    }

    private static boolean isInstructionLead(String body) {
        Matcher request = REQUEST.matcher(body);
        String afterRequest = request.find() ? body.substring(request.end()) : body;
        Matcher reminder = REMINDER.matcher(afterRequest);
        String remainder = reminder.find() ? afterRequest.substring(reminder.end()) : afterRequest;
        return !remainder.equals(body) && stripInstructionContext(remainder).isEmpty();
    }

    private static String followingClause(String[] parts, int index, String[] sentences, int sentenceIndex) {
        int remaining = 3;
        for (int next = sentenceIndex; next < sentences.length && remaining > 0; next++) {
            String[] clauses = next == sentenceIndex ? parts : sentences[next].strip().split("[,，]");
            for (int offset = next == sentenceIndex ? index + 1 : 0; offset < clauses.length && remaining > 0; offset++) {
                String text = stripSentenceEnd(clauses[offset].strip()).replaceFirst("^[噢哦](?=我|他|她|已经)", "");
                if (text.isEmpty()) continue;
                remaining--;
                if (!FOLLOWUP_FILLER.matcher(text).matches()) return text;
            }
        }
        return "";
    }

    private static boolean withdraws(String original, String following) {
        if (WITHDRAWAL.matcher(following).matches()) {
            return !following.endsWith("取消了") || MEETING_ACTION.matcher(original).find();
        }
        if (LOCAL_CANCEL.matcher(following).find()) return true;
        Matcher completed = COMPLETED_FOR_USER.matcher(following);
        if (completed.matches() && original.contains(completed.group(2))
                && (completed.group(1) == null || original.contains(completed.group(1)))) return true;
        if (following.matches("^(?:我)?(?:已经|刚才)(?:约[^,。!?;]{1,10})?修好了?[啦哈]?$") && original.contains("修")) return true;
        if (following.matches("^(?:人家|对方|他|她)说自己打车(?:过来|来)[了啦哈]?$") && original.contains("接")) return true;
        String withdrawal = following.startsWith("不") && following.endsWith("了") && !following.startsWith("不用")
                ? "别" + following.substring(1) : following;
        Matcher verb = WITHDRAWAL_ACTION.matcher(withdrawal);
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
        String rest = stripContext(time);
        String schedule = time.substring(0, time.length() - rest.length()).strip();
        return hasSchedule(schedule) ? schedule : "";
    }

    private static Match deferredTask(String priorAction, String reschedule) {
        Matcher marker = RESCHEDULE.matcher(reschedule);
        boolean correction = marker.find();
        Matcher verb = ACTION.matcher(priorAction);
        Matcher disposal = DEFER_DISPOSAL.matcher(priorAction);
        String priorVerb = verb.find() ? verb.group() : disposal.find() ? disposal.group(1) : "";
        if (priorVerb.isEmpty()) return null;
        String newSchedule = correction ? reschedule.substring(marker.end()) : reschedule;
        String rest = stripContext(newSchedule);
        if (!correction && !rest.startsWith("再")) return null;
        String time = newSchedule.substring(0, newSchedule.length() - rest.length()).strip();
        Matcher resumedVerb = ACTION.matcher(MODIFIER.matcher(rest).replaceFirst(""));
        if (!hasSchedule(time) || !resumedVerb.find() || !resumedVerb.group().equals(priorVerb)) return null;
        return new Match(priorAction, time);
    }

    private static String reportedDeadline(String previous, String clause) {
        Matcher delegation = TAKE_OVER.matcher(clause);
        Matcher report = REPORTED_DUTY.matcher(previous);
        if (!delegation.matches() || !report.find()) return "";
        String instruction = previous.substring(report.end());
        String remainder = stripContext(instruction);
        String time = instruction.substring(0, instruction.length() - remainder.length()).strip();
        Matcher requirement = REQUIRED_ACTION.matcher(remainder);
        if (!hasSchedule(time) || !requirement.find()) return "";
        String action = MODIFIER.matcher(remainder.substring(requirement.end())).replaceFirst("");
        String verb = delegation.group(1);
        return action.startsWith(verb) || (DISPOSAL.matcher(action).find() && action.contains(verb)) ? time : "";
    }

    private static String trailingTime(String[] parts, int index, String following, String clause) {
        boolean explicit = DEADLINE.matcher(following).lookingAt();
        if (following.isEmpty() || (!explicit && index + 2 != parts.length)) {
            return "";
        }
        String afterTime = stripContext(following);
        if (afterTime.isEmpty() || (explicit && afterTime.matches("^(?:发给|交给|传给|给|回)我(?:就行)?$"))) {
            return following;
        }
        Matcher period = TRAILING_PERIOD.matcher(following);
        if (period.matches() && TIME.matcher(clause).find()) return period.group(1);
        String appointment = APPOINTMENT_TIME.matcher(following).replaceFirst("");
        if (!appointment.equals(following) && hasSchedule(appointment) && stripContext(appointment).isEmpty()) {
            return appointment;
        }
        String use = stripContext(following);
        if (EVENT_USE.matcher(use).matches()) {
            String time = following.substring(0, following.length() - use.length()).strip();
            if (hasSchedule(time)) return time;
        }
        // A start time and its explicitly stated cutoff can belong to the same instruction.
        if (explicit && clause.contains("开始") && (hasDate(following) || TIME.matcher(following).find())) {
            return following;
        }
        return "";
    }

    static String stripContext(String text) {
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

    private static boolean isTaskClause(String body, boolean timed, boolean addressed, boolean chat, boolean imperativeDisposal) {
        body = REQUEST_PREFACE.matcher(body).replaceFirst("");
        if (body.startsWith("请问") || body.startsWith("请教") || body.startsWith("系统消息")
                || REPORTED.matcher(body).find() || CANCELLATION_NOTICE.matcher(body).find()) {
            return false;
        }

        Matcher request = REQUEST.matcher(body);
        boolean requested = request.find();
        String requestBody = stripInstructionContext(requested ? body.substring(request.end()).stripLeading() : body);
        if (requested) requestBody = REQUEST.matcher(requestBody).replaceFirst("");
        String reminderBody = MODIFIER.matcher(requestBody).replaceFirst("");
        Matcher reminder = REMINDER.matcher(reminderBody);
        boolean reminded = reminder.find();
        String instruction = reminded ? reminderBody.substring(reminder.end()).stripLeading() : requestBody;
        String withoutTime = stripInstructionContext(instruction);
        String action = stripInstructionContext(MODIFIER.matcher(withoutTime).replaceFirst(""));
        action = RECIPIENT.matcher(action).replaceFirst("");
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

        if (INFRA_NOTICE.matcher(action).matches() || NOTICE_ONLY.matcher(action).find()
                || action.matches("^(?:看到了|知道了|了解了|看见了)(?:就行|就好|就可以)(?:了)?[。!]*$")) return false;
        boolean checkRequested = (requested || reminded) && CHECK_ACTION.matcher(action).find();
        boolean explicitRequest = reminded || (requested && EXPLICIT_REQUEST.matcher(request.group()).find());
        if ((STATUS_QUESTION.matcher(action).find() && !checkRequested)
                || (!checkRequested && ALTERNATIVE_QUESTION.matcher(action).find())
                || (QUESTION_END.matcher(action).find() && !requested && !checkRequested
                && !(timed && COURTESY_QUESTION.matcher(action).find()))
                || (!checkRequested && (FINISHED.matcher(action).find() || DESCRIPTION.matcher(action).find()))
                || (!checkRequested && !explicitRequest && STATUS_UPDATE.matcher(stripSentenceEnd(action)).find())) {
            return false;
        }

        String content = stripSentenceEnd(action);
        if (timed && DIRECT_REQUEST.matcher(instruction).find() && (requested || !QUESTION_END.matcher(instruction).find())) return true;
        boolean disposal = DISPOSAL.matcher(content).find();
        if (disposal && !requested && !reminded && !imperativeDisposal && END_PARTICLE.matcher(action).find()) {
            return false;
        }
        if (reminded && content.length() >= 2 && !NON_ACTION.matcher(content).find()) {
            return true;
        }
        boolean modified = !action.equals(instruction);
        return disposal || DIRECT_REQUEST.matcher(content).find()
                || ((requested || timed) && (RELAY_OR_CALL.matcher(content).find() || RELAY_OR_CALL.matcher(withoutTime).find()))
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
        String task = WEEKDAY.matcher(DATE_REFERENCE.matcher(instruction).replaceAll(" ")).replaceAll("$0 ");
        String trailing = match.trailingTime();
        Matcher marker = DEADLINE.matcher(task);
        String explicit = trailing.isEmpty()
                ? (marker.find() ? task.substring(marker.end()) : "")
                : WEEKDAY.matcher(DEADLINE.matcher(trailing).replaceFirst("")).replaceAll("$0 ");
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
        Matcher lastWorkday = LAST_WORKDAY.matcher(dateText);
        Matcher monthEnd = MONTH_END.matcher(dateText);
        Matcher yearEnd = YEAR_END.matcher(dateText);
        Matcher dayOfMonth = DAY_OF_MONTH.matcher(dateText);
        Matcher duration = durationIn(explicit.isEmpty() ? task : explicit);

        try {
            LocalDate date = reference.toLocalDate();
            Matcher statedPeriod = TRAILING_PERIOD.matcher(trailing);
            String period = statedPeriod.matches() ? statedPeriod.group(1) : null;
            if (numeric.find()) {
                date = dateFrom(numeric, date);
            } else if (!absoluteOnly && yearDate.find()) {
                int offset = switch (yearDate.group(1)) {
                    case "明" -> 1;
                    case "后" -> 2;
                    default -> 0;
                };
                date = LocalDate.of(date.getYear() + offset, number(yearDate.group(2)), number(yearDate.group(3)));
            } else if (!absoluteOnly && monthDay.find()) {
                YearMonth month = YearMonth.from(date).plusMonths(monthDay.group(1).equals("下") ? 1 : 0);
                date = month.atDay(number(monthDay.group(2)));
            } else if (chinese.find() && (!absoluteOnly || FULL_CHINESE_DATE.matcher(chinese.group()).matches())) {
                date = dateFrom(chinese, date);
            } else if (absoluteOnly) {
                return null;
            } else if (shortDate.find()) {
                date = nextMonthDay(date, Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));
            } else if (duration != null) {
                long due = relativeDeadline(duration, reference);
                String unit = duration.group(3);
                String durationText = explicit.isEmpty() ? task : explicit;
                String after = durationText.substring(duration.end()).stripLeading();
                if ((unit.equals("天") || unit.equals("周")) && TIME.matcher(after).lookingAt()) {
                    LocalDate day = Instant.ofEpochMilli(due).atZone(reference.getZone()).toLocalDate();
                    return dateTimeFrom(day, after, null).atZone(reference.getZone()).toInstant().toEpochMilli();
                }
                return due;
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
            } else if (lastWorkday.find()) {
                if (lastWorkday.group().startsWith("下")) date = date.plusMonths(1);
                date = date.with(TemporalAdjusters.lastDayOfMonth());
                if (date.getDayOfWeek() == DayOfWeek.SUNDAY) date = date.minusDays(2);
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY) date = date.minusDays(1);
            } else if (monthEnd.find()) {
                if (monthEnd.group().startsWith("下")) {
                    date = date.plusMonths(1);
                }
                date = date.with(TemporalAdjusters.lastDayOfMonth());
            } else if (yearEnd.find()) {
                date = date.with(TemporalAdjusters.lastDayOfYear());
            } else if (dayOfMonth.find()) {
                date = nextDayOfMonth(date, number(dayOfMonth.group(1)));
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
                || RELATIVE.matcher(text).find() || WEEKDAY.matcher(text).find() || LAST_WORKDAY.matcher(text).find() || MONTH_END.matcher(text).find()
                || YEAR_END.matcher(text).find() || DAY_OF_MONTH.matcher(text).find();
    }

    static boolean hasSchedule(String text) {
        return hasDate(text) || TIME.matcher(text).find() || durationIn(text) != null;
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
            if ("中午".equals(defaultPeriod) || NOON.matcher(text).find()) return date.atTime(12, 0);
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
        String before = time.group(7);
        int offset = before == null ? 0 : switch (before) {
            case "一刻" -> 15;
            case "半小时" -> 30;
            default -> number(before.substring(0, before.length() - "分钟".length()));
        };
        if (hour == 24 && minute == 0) {
            return date.plusDays(1).atStartOfDay().minusMinutes(offset);
        }
        if (period != null) {
            switch (period) {
                case "晚上", "晚间", "夜里", "晚" -> {
                    if (hour == 0 || hour == 12) return date.plusDays(1).atTime(0, minute).minusMinutes(offset);
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
        return date.atTime(hour, minute).minusMinutes(offset);
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
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replace("\r\n", "\n").trim()
                .replace("今儿", "今天").replace("今晚上", "今晚").replace("明日", "明天");
        normalized = MESSAGE_DECORATION.matcher(normalized).replaceFirst("");
        normalized = normalized.replaceAll("(?<=前|后)[—–]{1,3}(?=请|记得|麻烦|把|将)", "")
                .replaceAll("(?<=前|后)[❗‼️]+(?=把|将|请|记得)", "")
                .replace("清早", "早上").replace("半个钟头又", "半小时")
                .replaceFirst("(?i)^deadline\\s*(?=改|挪|推)", "时间");
        Matcher gaps = DICTATION_SPACES.matcher(normalized);
        if (!normalized.startsWith("@") && gaps.find() && gaps.find() && gaps.find()) {
            normalized = gaps.replaceAll("");
        }
        Matcher boxed = TIME_TOPIC_BOX.matcher(normalized);
        if (boxed.find() && hasSchedule(boxed.group(1)) && stripContext(boxed.group(1)).isEmpty()) {
            normalized = boxed.group(1) + normalized.substring(boxed.end());
        }
        normalized = DATE_PARTICLE.matcher(normalized).replaceAll("$1");
        normalized = BEFORE_HOUR_WORDING.matcher(normalized).replaceAll("差$1");
        normalized = EVENING_CLOCK.matcher(normalized).replaceAll("$1");
        normalized = MINUTES_PAST_HOUR.matcher(normalized).replaceAll("$1");
        return MINUTES_TO_HOUR.matcher(normalized).replaceAll("$2差$1");
    }
}
