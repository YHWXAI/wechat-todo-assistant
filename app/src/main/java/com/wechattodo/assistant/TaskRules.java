package com.wechattodo.assistant;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskRules {
    private static final Pattern REQUEST = Pattern.compile(
            "请(?!问|教)|麻烦|帮我|帮忙|记得|别忘|需要你|辛苦|务必|待办|提醒我");
    private static final Pattern ACTION = Pattern.compile(
            "发(?!生|现|烧)|回(?!家)|交|提[交供]|处[理]|确认|审核|整理|完成|开会|参会|"
                    + "修改|更新|安排|准备|提交|跟进|联系|付款|支付|打电话|查[看询]|检查|带|取|买|签|给");
    private static final Pattern RELATIVE = Pattern.compile(
            "大后天|后天|明天|明早|明晚|今天|今晚|今早");
    private static final Pattern CHINESE_DATE = Pattern.compile(
            "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})(?:日|号)?(?!\\d)");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)");
    private static final Pattern FULL_CHINESE_DATE = Pattern.compile("\\d{4}年\\d{1,2}月\\d{1,2}[日号]");
    private static final Pattern CHAT_REQUEST = Pattern.compile(
            "(?:^|[，。！；,\\s]|早上|上午|中午|下午|晚上|下班)你(?:先|再|顺便|可以)?"
                    + "(?:买|带|拿|取|接|发|交|准备|联系|处理)"
                    + "|(?:回来|回家|下班)(?:的时候|时候|路上|前|后)(?:顺便)?(?:带|买|拿|取)");
    private static final Pattern CHECKING_STATUS = Pattern.compile(
            "(?:买|带|拿|取|接|发|交)(?:了|完|到了)(?:没有|没|吗|么)|(?:怎么买|去哪儿买|哪里买)");
    private static final String HOUR = "[0-9零〇一二两三四五六七八九十]{1,3}";
    private static final Pattern TIME = Pattern.compile(
            "(凌晨|早上|上午|中午|下午|傍晚|晚上|晚间|夜里|早|晚)?\\s*(?<!\\d)("
                    + HOUR + ")(?::([0-9]{2})(?![0-9])|[点时](?:(半|一刻|三刻)|("
                    + HOUR + ")分)?)");
    private static final LocalTime DEFAULT_TIME = LocalTime.of(18, 0);

    private TaskRules() {}

    static boolean isTask(String message) {
        String text = normalize(message);
        if (text.startsWith("请问") || text.startsWith("请教") || text.startsWith("系统消息")) {
            return false;
        }
        boolean hasDate = RELATIVE.matcher(text).find() || CHINESE_DATE.matcher(text).find()
                || NUMERIC_DATE.matcher(text).find();
        return (REQUEST.matcher(text).find() || hasDate) && ACTION.matcher(text).find();
    }

    static boolean isChatTask(String message) {
        if (isTask(message)) {
            return true;
        }
        String text = normalize(message);
        return CHAT_REQUEST.matcher(text).find() && !CHECKING_STATUS.matcher(text).find();
    }

    static Long dueFromChat(String message, ZonedDateTime scanned) {
        String text = normalize(message);
        if (!NUMERIC_DATE.matcher(text).find() && !FULL_CHINESE_DATE.matcher(text).find()) {
            return null;
        }
        Long due = dueAt(text, scanned);
        return due != null && due > scanned.toInstant().toEpochMilli() ? due : null;
    }

    static Long dueAt(String message, ZonedDateTime received) {
        String text = normalize(message);
        Matcher numeric = NUMERIC_DATE.matcher(text);
        Matcher chinese = CHINESE_DATE.matcher(text);
        Matcher relative = RELATIVE.matcher(text);
        try {
            LocalDate date = received.toLocalDate();
            String period = null;
            if (numeric.find()) {
                date = dateFrom(numeric, date);
            } else if (chinese.find()) {
                date = dateFrom(chinese, date);
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
            } else if (!TIME.matcher(text).find()) {
                return null;
            }
            return date.atTime(timeFrom(text, period)).atZone(received.getZone())
                    .toInstant().toEpochMilli();
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private static LocalDate dateFrom(Matcher match, LocalDate today) {
        String year = match.group(1);
        int month = Integer.parseInt(match.group(2));
        int day = Integer.parseInt(match.group(3));
        return LocalDate.of(year == null ? today.getYear() : Integer.parseInt(year), month, day);
    }

    private static LocalTime timeFrom(String text, String defaultPeriod) {
        Matcher time = TIME.matcher(text);
        if (!time.find()) {
            if ("早".equals(defaultPeriod)) {
                return LocalTime.of(9, 0);
            }
            return DEFAULT_TIME;
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
        } else if (time.group(5) != null) {
            minute = number(time.group(5));
        }
        if (period != null) {
            switch (period) {
                case "下午", "傍晚", "晚上", "晚间", "夜里", "晚" -> {
                    if (hour >= 1 && hour < 12) hour += 12;
                }
                case "中午" -> {
                    if (hour >= 1 && hour <= 5) hour += 12;
                }
                case "凌晨", "早上", "上午", "早" -> {
                    if (hour == 12) hour = 0;
                }
            }
        }
        return LocalTime.of(hour, minute);
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
        return Normalizer.normalize(text, Normalizer.Form.NFKC).trim();
    }
}
