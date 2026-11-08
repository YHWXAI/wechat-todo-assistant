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
    private enum Target { UNSPECIFIED, SELF, ALL, OTHER, UNCERTAIN }
    record Decision(Action action, String text) {}
    private record Address(Target target, int end, String lead) {}
    private record Scope(Target target, String text) {}

    private static final Pattern QUOTE = Pattern.compile("“[^”]*”|「[^」]*」|『[^』]*』|‘[^’]*’|《[^》]*》|\"[^\"\\n]*\"");
    private static final Pattern FORWARD = Pattern.compile("^【(?:转发的)?聊天记录】");
    private static final Pattern BREAK = Pattern.compile("[,，。!?;；\\r\\n]+");
    private static final Pattern SENTENCE = Pattern.compile("[。!?;；\\r\\n]");
    private static final Pattern LEADING_SPACE = Pattern.compile("^[\\s,，:：、]+");
    private static final Pattern MENTION = Pattern.compile("^@([^@\\s,，:：、。!?;]{1,40})(?=[@\\s,，:：、]|$)");
    private static final Pattern REQUEST = Pattern.compile("^(?:请|麻烦|劳烦|有劳|辛苦)\\s*");
    private static final Pattern NOTICE_LABEL = Pattern.compile("^(?:群通知|通知)[:：]\\s*");
    private static final Pattern COMMON_NICKNAME = Pattern.compile("^[小老][王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗梁宋郑谢韩唐许邓曹彭曾萧肖田董袁潘于蒋蔡余杜叶程魏苏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱戴严莫孔向汤]");
    private static final Pattern INSTRUCTION_START = Pattern.compile(
            "^(?:今天|今晚|明天|明早|后天|下周|周[一二三四五六日天]|星期|上午|下午|记得|别忘|把|将|负责|帮我|帮忙"
            + "|发(?:送|给|我|设计稿|合同|报价|报表|周报|文件|材料)|提交|整理|核对|检查|联系|寄(?:出|回|合同|样品))");
    private static final String NAME_TIME = "(?:(?:今天|明天|明早|后天|(?:下周|周|星期)[一二三四五六日天])(?:上午|下午|晚上)?|上午|下午|晚上|今晚)";
    private static final Pattern BEFORE_NAME = Pattern.compile("^" + NAME_TIME + "\\s*(?=请|麻烦|劳烦|有劳|辛苦)");
    private static final Pattern RESPONSIBILITY = Pattern.compile("^(" + NAME_TIME + "\\s*)?负责");
    private static final Pattern ALL_WORD = Pattern.compile("^(?:大家|各位|所有人|全体成员)(?=[\\s,，:：、]|今天|今晚|明天|明早|后天|周|请|记得|把|务必|$)");
    private static final Pattern RESTRICTED_ALL = Pattern.compile("除外|除[^，。]{1,18}以外|仅限|只限|不用参加|无需参加");
    private static final Set<String> ALL = Set.of("所有人", "全体成员");
    private final Set<String> mine;
    private final Set<String> ambiguous;
    private final List<String> knownNames;

    RecipientRules(Set<String> mine, Set<String> ambiguous) {
        this.mine = mine;
        this.ambiguous = ambiguous;
        knownNames = java.util.stream.Stream.of(mine, ambiguous, ALL).flatMap(Set::stream)
                .distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    Decision evaluate(String message, Context context) {
        String text = Normalizer.normalize(message, Normalizer.Form.NFKC).replace('\u200b', ' ').strip();
        if (FORWARD.matcher(text).find()) {
            // A forwarded card isn't direct evidence of the addressee. Keep the intent rules for a real tail.
            return new Decision(TaskRules.isChatTask(text) ? Action.CONFIRM : Action.IGNORE, text);
        }
        Decision candidate = new Decision(Action.IGNORE, "");
        for (Scope scope : scopes(text)) {
            if (scope.target() == Target.OTHER || scope.text().isBlank()) continue;
            String content = NOTICE_LABEL.matcher(scope.text()).replaceFirst("");
            if (scope.target() == Target.ALL) content = "@所有人 " + content;
            TaskRules.Match task = TaskRules.notificationTask(content);
            boolean routed = scope.target() == Target.SELF
                    || (scope.target() == Target.ALL && !RESTRICTED_ALL.matcher(content).find());
            boolean privateRequest = context == Context.PRIVATE && scope.target() == Target.UNSPECIFIED;
            String selected = content.equals(text) ? message : content;
            if (task != null && (privateRequest || (routed && beginsWithTask(content, task)))) {
                return new Decision(Action.AUTO, selected);
            }
            boolean addressed = scope.target() == Target.SELF || scope.target() == Target.UNCERTAIN;
            if (candidate.action() == Action.IGNORE && (task != null || TaskRules.isChatTask(content)
                    || (addressed && TaskRules.isChatTask("@收件人 " + content)))) {
                candidate = new Decision(Action.CONFIRM, selected);
            }
        }
        return candidate;
    }

    private static boolean beginsWithTask(String content, TaskRules.Match full) {
        String first = SENTENCE.split(content, 2)[0];
        TaskRules.Match firstTask = TaskRules.notificationTask(first);
        return firstTask != null && firstTask.clause().strip().replaceFirst("[。!?;；]+$", "").equals(
                full.clause().strip().replaceFirst("[。!?;；]+$", ""));
    }

    private List<Scope> scopes(String original) {
        String visible = QUOTE.matcher(original).replaceAll(match -> " ".repeat(match.group().length()));
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
                scopes.add(new Scope(current, content.toString().strip()));
                current = pendingMention && startsWithMention ? merge(current, address.target()) : address.target();
                content.setLength(0);
                content.append(address.lead());
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
            mentions = merge(mentions, target(name));
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
        return null;
    }

    private Address directName(String text, boolean followedByComma) {
        Matcher time = BEFORE_NAME.matcher(text);
        int start = time.find() ? time.end() : 0;
        Address address = nameAfterTime(text.substring(start), followedByComma);
        return address == null ? null : new Address(address.target(), start + address.end(),
                text.substring(0, start) + address.lead());
    }

    private Address nameAfterTime(String text, boolean followedByComma) {
        Matcher request = REQUEST.matcher(text);
        int start = request.find() ? request.end() : 0;
        String rest = text.substring(start);
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
                || (action.isEmpty() && followedByComma) || (skip > 0 && !action.isEmpty());
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
        return Target.OTHER;
    }
}
