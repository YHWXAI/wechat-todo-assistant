package com.wechattodo.assistant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RecipientRules {
    enum Context { GROUP, PRIVATE }
    enum Action { AUTO, CONFIRM, IGNORE }
    private enum Target { UNSPECIFIED, SELF, ALL, OTHER, UNCERTAIN, UNVERIFIED_MENTION }
    record Decision(Action action, String text) {}
    private record Address(Target target, int end, String lead) {}
    private record Scope(Target target, String text) {}

    private static final Pattern QUOTE = Pattern.compile("“[^”]*”|「[^」]*」|『[^』]*』|‘[^’]*’|《[^》]*》|\"[^\"\\n]*\"");
    private static final Pattern FORWARD = Pattern.compile("^【(?:转发的)?聊天记录】");
    private static final Pattern BREAK = Pattern.compile("[,，。!?;；\\r\\n]+");
    private static final Pattern SENTENCE = Pattern.compile("[。!?;；\\r\\n]");
    private static final Pattern SENTENCE_CHUNKS = Pattern.compile("(?<=[。!?;；\\r\\n])");
    private static final Pattern LEADING_SPACE = Pattern.compile("^[\\s,，:：、]+");
    private static final Pattern MENTION = Pattern.compile("^@([^@\\s,，:：、。!?;]{1,40})(?=[@\\s,，:：、]|$)");
    private static final Pattern REQUEST = Pattern.compile("^(?:请|麻烦|劳烦|有劳|辛苦)\\s*");
    private static final Pattern NOTICE_LABEL = Pattern.compile("^(?:群通知|通知)[:：]\\s*");
    private static final Pattern COMMON_NICKNAME = Pattern.compile("^[小老][王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗梁宋郑谢韩唐许邓曹彭曾萧肖田董袁潘于蒋蔡余杜叶程魏苏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱戴严莫孔向汤]");
    private static final Pattern INLINE_MENTION = Pattern.compile("^([^@。!?;]{0,24}?)(?:请|麻烦|辛苦|有劳)\\s*(?=@)");
    private static final Pattern INLINE_NAME = Pattern.compile("^([\\p{IsHan}]{1,16}?)(?:辛苦|麻烦|有劳|劳烦)\\s*");
    private static final Pattern GROUP_HEADING = Pattern.compile("^(?:分工(?:更新|安排)?|任务分配)[:：]\\s*");
    private static final Pattern MENTION_GROUP_LEAD = Pattern.compile("^(?:两位搭档|这两位|以下同学|请以下人员)\\s*(?=@)");
    private static final Pattern COPY_ONLY = Pattern.compile("^(?:这(?:条|里)只抄送你|转告你一下)(?=[,，。!]|$)");
    private static final Pattern RECRUIT_HELP = Pattern.compile(
            "(?:有人|哪位)[^。!?;\\r\\n]{1,45}(?:核对|办理|去|帮)[^。!?;\\r\\n]{1,25}[?？]"
            + "\\s*需要(?:一位|一名)[^,。!?;]{1,18}(?:同事|组员|志愿者|成员)");
    private static final Pattern ROLE_ONLY = Pattern.compile(
            "(?:这项|这件事)?只(?:需要|让)[^,，。!?;]{1,12}(?:做|处理|提交|参加)(?=[,，。!?;]|$)"
            + "|除[^,，。!?;]{1,12}外(?:都)?(?:不用|无需)(?:过去|参加|处理|提交)");
    private static final Pattern INSTRUCTION_START = Pattern.compile(
            "^(?:今天|今晚|明天|明早|后天|下周|周[一二三四五六日天]|星期|上午|下午|记得|别忘|把|将|负责|帮我|帮忙|麻烦|辛苦|拜托|请"
            + "|发(?:送|给|我|设计稿|合同|报价|报表|周报|文件|材料)|提交|整理|核对|检查|联系|寄(?:出|回|合同|样品))");
    private static final String NAME_TIME = "(?:(?:今天|明天|明早|后天|(?:下周|周|星期)[一二三四五六日天])(?:上午|下午|晚上)?|上午|下午|晚上|今晚)";
    private static final Pattern BEFORE_NAME = Pattern.compile("^" + NAME_TIME + "\\s*(?=请|麻烦|劳烦|有劳|辛苦)");
    private static final Pattern RESPONSIBILITY = Pattern.compile("^(" + NAME_TIME + "\\s*)?负责");
    private static final Pattern ALL_WORD = Pattern.compile("^(?:大家|各位|所有人|全体成员)(?=[\\s,，:：、]|今天|今晚|明天|明早|后天|周|请|记得|把|务必|$)");
    private static final Pattern EACH_MEMBER = Pattern.compile("^(?:每个人|每位成员|我们所有人)都");
    private static final Pattern VOLUNTEER_REQUEST = Pattern.compile(
            "^(?:[^。!?;\\r\\n]*[,，]\\s*)?(?:谁|哪位)(?:有空|方便)(?:谁|就)?(?:弄|做|处理|接|来|去|帮)"
            + "[^,，。!?;\\r\\n]*[。!?;；]*$");
    private static final Pattern PRIVATE_REPLY_REQUEST = Pattern.compile(
            "^(?:(?=[^。!?;\\r\\n]*(?:给我|发我|回我|回复我))[^。!?;\\r\\n]*"
            + "[,，]\\s*(?:别|不要|不用)(?:再)?(?:往群(?:里|内)?发|发(?:到)?群(?:里|内)?)"
            + "(?:了|哈|哦|就行)?|[^。!?;\\r\\n]*(?:私(?:发|信)(?:给)?我|单独(?:发|回)(?:给)?我)[^。!?;\\r\\n]*)[。!?;；]*$");
    private static final Pattern RESTRICTED_ALL = Pattern.compile(
            "除外|除[^,，。]{1,18}以外|仅限|只限|不用参加|无需参加"
            + "|(?:只有|只让|仅有)[^,，。!?;]{1,24}(?:参加|到场|开会|提交|负责|处理|出席)");
    private static final Pattern EXCLUDES_US = Pattern.compile("(?:咱俩|我俩|咱们|我们(?:俩|两个)?)(?:都)?(?:不用|无需|不必)(?:再)?(?:准备|参加|到场|处理|办理|提交)(?:了|哈)?[。!]*$");
    private static final Pattern ROLE_ADDRESS = Pattern.compile(
            "^[\\p{IsHan}]{1,12}?(?:的同学|的同事|的师傅)(?=今天|明天|明早|后天|周|星期|礼拜|请|麻烦|记得)");
    private static final Set<String> ALL = Set.of("所有人", "全体成员");
    private final Set<String> mine;
    private final Set<String> ambiguous;
    private final List<String> knownNames;
    private final Pattern personalExemption;

    RecipientRules(Set<String> mine, Set<String> ambiguous) {
        this.mine = mine;
        this.ambiguous = ambiguous;
        knownNames = java.util.stream.Stream.of(mine, ambiguous, ALL).flatMap(Set::stream)
                .distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        String aliases = mine.stream().filter(name -> !ambiguous.contains(name)).map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
        if (aliases.isEmpty()) {
            personalExemption = null;
        } else {
            String name = "(?:" + aliases + ")";
            String actions = "(?:提交|上传|填写|参加|到场|办理|处理|交|发|填|带|领|过去|来|去)";
            String noNeed = "(?:不用|无需|不必|可以不|免)(?:再)?";
            personalExemption = Pattern.compile("(?:^|[,，。!?;；\\r\\n])\\s*(?:"
                    + "(?:(?:已经|已)(?:交过|提交过|发过|办过)的)?" + name
                    + "(?:(?:这次|今天|明天|这轮|(?:的那份)?(?:已经|已)(?:交|提交|培训|参加|发)(?:过)?了?)?"
                    + noNeed + actions + "?(?:这一项|这项)?|(?:已经|已)(?:交|提交|发)(?:过)?了|(?:已经|已)(?:培训|参加)过了?[,，]\\s*不用再(?:来|参加|到场))"
                    + "|(?:这次|这轮|今天)?(?:不用|无需|不需要)" + name + "(?:再)?" + actions + "?"
                    + "|除(?:了)?" + name + "(?:之外|外)?都(?:要|需要|得)" + actions + ")"
                    + "(?:了|哈|啊)?(?=[,，。!?;；\\r\\n]|$)");
        }
    }

    Decision evaluate(String message, Context context) {
        List<Decision> decisions = evaluateAll(message, context);
        return decisions.isEmpty() ? new Decision(Action.IGNORE, "") : decisions.get(0);
    }

    List<Decision> evaluateAll(String message, Context context) {
        String text = Normalizer.normalize(message, Normalizer.Form.NFKC).replace('\u200b', ' ').strip();
        if (context == Context.GROUP) text = GROUP_HEADING.matcher(text).replaceFirst("");
        else text = text.replaceFirst("^@我[?？]\\s*(?:语音认错了|语音转写有误)[,，。]\\s*", "");
        if (FORWARD.matcher(text).find()) {
            String tail = TaskRules.afterForwardedBlock(text);
            if (context == Context.PRIVATE && !tail.isEmpty()) return evaluateAll(tail, context);
            return TaskRules.isChatTask(text) ? List.of(new Decision(Action.CONFIRM, text)) : List.of();
        }
        List<Decision> automatic = new ArrayList<>();
        Decision candidate = null;
        for (Scope scope : scopes(text)) {
            Target target = scope.target();
            boolean uncertain = target == Target.UNCERTAIN || target == Target.UNVERIFIED_MENTION;
            if (target == Target.OTHER || scope.text().isBlank()
                    || (context == Context.GROUP && target == Target.UNVERIFIED_MENTION)
                    || (context == Context.PRIVATE && uncertain && EXCLUDES_US.matcher(scope.text()).find())) continue;
            String content = NOTICE_LABEL.matcher(scope.text()).replaceFirst("");
            if (context == Context.GROUP && target == Target.UNSPECIFIED) {
                Matcher everyone = EACH_MEMBER.matcher(content);
                if (everyone.find()) {
                    target = Target.ALL;
                    content = content.substring(everyone.end()).stripLeading();
                } else {
                    content = SENTENCE_CHUNKS.splitAsStream(content).filter(sentence -> {
                        String line = sentence.strip();
                        return !VOLUNTEER_REQUEST.matcher(line).matches() && !PRIVATE_REPLY_REQUEST.matcher(line).matches();
                    }).collect(java.util.stream.Collectors.joining()).strip();
                    if (content.isEmpty()) continue;
                    content = ROLE_ADDRESS.matcher(content).replaceFirst("");
                }
            }
            if (context == Context.GROUP && COPY_ONLY.matcher(content).find()) continue;
            if (target == Target.ALL) {
                content = withoutExcludedTasks(content, ROLE_ONLY);
                if (content.isEmpty()) continue;
            }
            if (context == Context.GROUP && (target == Target.ALL || target == Target.UNSPECIFIED)
                    && personalExemption != null) {
                content = withoutExcludedTasks(content, personalExemption);
                if (content.isEmpty()) continue;
            }
            boolean routed = target == Target.SELF || (target == Target.ALL && !RESTRICTED_ALL.matcher(content).find());
            boolean privateRequest = context == Context.PRIVATE && target == Target.UNSPECIFIED;
            List<String> units = context == Context.GROUP && routed
                    ? TaskRules.independentlyScheduledTasks(content) : List.of(content);
            for (String unit : units) {
                if (target == Target.ALL) unit = "@所有人 " + unit;
                TaskRules.Match task = TaskRules.notificationTask(unit);
                String selected = unit.equals(text) ? message : unit;
                if (task != null && !TaskRules.needsTimingConfirmation(unit, task)
                        && (privateRequest || (routed && beginsWithTask(unit, task)))) {
                    Decision decision = new Decision(Action.AUTO, selected);
                    if (context == Context.PRIVATE) return List.of(decision);
                    if (!automatic.contains(decision)) automatic.add(decision);
                } else if (candidate == null && (task != null || TaskRules.isChatTask(unit)
                        || (context == Context.GROUP && target == Target.UNSPECIFIED && TaskRules.hasSchedule(unit)
                        && RECRUIT_HELP.matcher(unit).find())
                        || ((target == Target.SELF || uncertain) && TaskRules.isChatTask("@收件人 " + unit)))) {
                    candidate = new Decision(Action.CONFIRM, selected);
                }
            }
        }
        return !automatic.isEmpty() ? automatic : candidate != null ? List.of(candidate) : List.of();
    }

    private static String withoutQuotes(String text) {
        return QUOTE.matcher(text).replaceAll(match -> " ".repeat(match.group().length()));
    }

    private static String withoutExcludedTasks(String text, Pattern exemptionPattern) {
        if (!exemptionPattern.matcher(withoutQuotes(text)).find()) return text;
        List<String> remaining = new ArrayList<>();
        boolean previousRetained = false;
        for (String chunk : SENTENCE_CHUNKS.split(text)) {
            String sentence = chunk.strip();
            if (sentence.isEmpty()) continue;
            Matcher exemption = exemptionPattern.matcher(withoutQuotes(sentence));
            if (exemption.find()) {
                if (exemption.start() == 0 && previousRetained) remaining.remove(remaining.size() - 1);
                previousRetained = false;
            } else {
                remaining.add(chunk);
                previousRetained = true;
            }
        }
        return String.join("", remaining).strip();
    }

    private static boolean beginsWithTask(String content, TaskRules.Match full) {
        String first = SENTENCE.split(content, 2)[0];
        TaskRules.Match firstTask = TaskRules.notificationTask(first);
        if (firstTask == null && TaskRules.hasSchedule(first) && TaskRules.stripContext(first).isEmpty()) return true;
        return firstTask != null && firstTask.clause().strip().replaceFirst("[。!?;；]+$", "").equals(
                full.clause().strip().replaceFirst("[。!?;；]+$", ""));
    }

    private List<Scope> scopes(String original) {
        String visible = withoutQuotes(original);
        List<Scope> scopes = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        Target current = Target.UNSPECIFIED;
        Matcher divider = BREAK.matcher(visible);
        int start = 0;
        boolean pendingMention = false;
        boolean afterComma = false;
        while (start < visible.length()) {
            boolean hasDivider = divider.find();
            int end = hasDivider ? divider.start() : visible.length();
            int next = hasDivider ? divider.end() : end;
            String part = visible.substring(start, end);
            int padding = part.length() - part.stripLeading().length();
            boolean followedByComma = hasDivider && divider.group().matches("[,，]+");
            boolean startsWithMention = part.stripLeading().startsWith("@");
            Address address = address(part.stripLeading(), followedByComma);
            Target trailing = address == null && current == Target.UNSPECIFIED && afterComma && !followedByComma
                    ? standaloneName(part.strip()) : null;
            if (trailing != null) {
                scopes.add(new Scope(trailing, content.toString().strip()));
                current = Target.UNSPECIFIED;
                content.setLength(0);
                pendingMention = false;
            } else if (address == null) {
                content.append(original, start, next);
                pendingMention = false;
            } else {
                String prior = content.toString().strip();
                String lead = "";
                if (current == Target.UNSPECIFIED && afterComma && prior.endsWith(",")) {
                    String time = prior.substring(0, prior.length() - 1).stripTrailing();
                    if (TaskRules.hasSchedule(time) && TaskRules.stripContext(time).isEmpty()) lead = time + " ";
                }
                if (lead.isEmpty()) scopes.add(new Scope(current, prior));
                current = pendingMention && startsWithMention ? merge(current, address.target()) : address.target();
                content.setLength(0);
                content.append(lead).append(address.lead());
                if (start + padding + address.end() < end) {
                    content.append(original, start + padding + address.end(), next);
                }
                pendingMention = startsWithMention && followedByComma && content.isEmpty();
            }
            afterComma = followedByComma;
            start = next;
        }
        scopes.add(new Scope(current, content.toString().strip()));
        return scopes;
    }

    private Address address(String text, boolean followedByComma) {
        int end = 0;
        Target mentions = null;
        while (end < text.length() && text.charAt(end) == '@') {
            String tail = text.substring(end);
            String name = knownNames.stream().filter(n -> tail.startsWith("@" + n) && boundary(tail, n.length() + 1))
                    .findFirst().orElse(null);
            if (name == null) {
                Matcher generic = MENTION.matcher(tail);
                if (!generic.find()) break;
                name = generic.group(1);
            }
            Target addressee = target(name);
            // A configured shared alias still carries evidence that the mention may be for this user.
            if (addressee == Target.UNCERTAIN && !ambiguous.contains(name)) addressee = Target.UNVERIFIED_MENTION;
            mentions = merge(mentions, addressee);
            end += name.length() + 1;
            Matcher spaces = LEADING_SPACE.matcher(text.substring(end));
            if (spaces.find()) end += spaces.end();
        }
        String remainder = text.substring(end);
        Address direct = directName(remainder, followedByComma);
        if (direct != null) return new Address(direct.target(), end + direct.end(), direct.lead());
        if (mentions != null) return new Address(mentions, end, "");
        Matcher all = ALL_WORD.matcher(text);
        if (all.find()) return new Address(Target.ALL, all.end(), "");

        String afterTime = TaskRules.stripContext(text);
        int leadLength = text.length() - afterTime.length();
        if (afterTime.startsWith("@") && leadLength > 0 && TaskRules.hasSchedule(text.substring(0, leadLength))) {
            Address inline = address(afterTime, followedByComma);
            if (inline != null) return new Address(inline.target(), leadLength + inline.end(), text.substring(0, leadLength) + inline.lead());
        }
        Matcher leadingMention = MENTION_GROUP_LEAD.matcher(text);
        if (leadingMention.find()) {
            Address inline = address(text.substring(leadingMention.end()), followedByComma);
            if (inline != null) return new Address(inline.target(), leadingMention.end() + inline.end(), inline.lead());
        }
        Matcher request = INLINE_MENTION.matcher(text);
        if (request.find()) {
            Address inline = address(text.substring(request.end()), followedByComma);
            if (inline != null) return new Address(inline.target(), request.end() + inline.end(),
                    request.group(1).strip() + inline.lead());
        }
        return null;
    }

    private Address directName(String text, boolean followedByComma) {
        Matcher time = BEFORE_NAME.matcher(text);
        int start = time.find() ? time.end() : 0;
        Address address = nameAfterTime(text.substring(start), followedByComma);
        if (address == null && start == 0) {
            Matcher inline = INLINE_NAME.matcher(text);
            if (inline.find()) {
                Address named = nameAfterTime(text.substring(inline.end()), followedByComma);
                if (named != null) return new Address(named.target(), inline.end() + named.end(),
                        inline.group(1) + named.lead());
            }
            String remainder = TaskRules.stripContext(text);
            int timeLength = text.length() - remainder.length();
            if (timeLength > 0 && TaskRules.hasSchedule(text.substring(0, timeLength))) {
                start = timeLength;
                address = nameAfterTime(remainder, followedByComma);
            }
        }
        return address == null ? null : new Address(address.target(), start + address.end(),
                text.substring(0, start) + address.lead());
    }

    private Address nameAfterTime(String text, boolean followedByComma) {
        Matcher request = REQUEST.matcher(text);
        int start = request.find() ? request.end() : 0;
        String rest = text.substring(start);
        if (rest.startsWith("(")) {
            int close = rest.indexOf(')');
            if (close > 1 && knownNames.contains(rest.substring(1, close))) {
                String name = rest.substring(1, close);
                String after = rest.substring(close + 1).stripLeading();
                if (INSTRUCTION_START.matcher(after).find()) {
                    return new Address(target(name), text.length() - after.length(), "");
                }
            }
        }
        String name = knownNames.stream().filter(n -> !ALL.contains(n) && rest.startsWith(n)).findFirst().orElse(null);
        if (name == null) {
            Matcher common = COMMON_NICKNAME.matcher(rest);
            if (!common.find()) return null;
            name = common.group();
        }
        String tail = rest.substring(name.length());
        Matcher spaces = LEADING_SPACE.matcher(tail);
        int skip = spaces.find() ? spaces.end() : 0;
        String action = tail.substring(skip);
        Matcher responsibility = RESPONSIBILITY.matcher(action);
        boolean assigned = responsibility.find();
        boolean explicit = assigned || (start > 0 && INSTRUCTION_START.matcher(action).find())
                || (action.isEmpty() && followedByComma) || (skip > 0 && !action.isEmpty())
                || (!action.isEmpty() && INSTRUCTION_START.matcher(action).find() && TaskRules.notificationTask(action) != null);
        if (!explicit) return null;
        String lead = assigned ? (responsibility.group(1) == null ? "" : responsibility.group(1)) + "请"
                : start > 0 ? "请" : "";
        return new Address(target(name), start + name.length() + skip + (assigned ? responsibility.end() : 0), lead);
    }

    private Target standaloneName(String name) {
        return !ALL.contains(name) && (knownNames.contains(name) || COMMON_NICKNAME.matcher(name).matches())
                ? target(name) : null;
    }

    private Target target(String name) {
        if (ambiguous.contains(name)) return Target.UNCERTAIN;
        if (mine.contains(name)) return Target.SELF;
        if (ALL.contains(name)) return Target.ALL;
        return mine.isEmpty() && !knownNames.contains(name) ? Target.UNCERTAIN : Target.OTHER;
    }

    private static boolean boundary(String text, int index) {
        return index == text.length() || Character.isWhitespace(text.charAt(index))
                || "@,，:：、".indexOf(text.charAt(index)) >= 0;
    }

    private static Target merge(Target first, Target next) {
        if (first == Target.SELF || next == Target.SELF) return Target.SELF;
        if (first == Target.ALL || next == Target.ALL) return Target.ALL;
        if (first == Target.UNCERTAIN || next == Target.UNCERTAIN) return Target.UNCERTAIN;
        if (first == Target.UNVERIFIED_MENTION || next == Target.UNVERIFIED_MENTION) return Target.UNVERIFIED_MENTION;
        return Target.OTHER;
    }
}
