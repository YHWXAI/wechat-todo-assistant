package com.wechattodo.assistant;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes explicit English requests when a concrete time is present. */
final class EnglishTaskRules {
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");
    private static final String VERBS = "(?:send|return|upload|update|bring|print|take|file|collect|submit|"
            + "deliver|pick up|call|email|pay|sign|review|check|finish|confirm|drop off|book)";
    private static final String DIRECT = "(?:please\\s+|remember to\\s+|don't forget to\\s+|"
            + "i need you to\\s+|(?:could|can|would|will) you\\s+(?:please\\s+)?)";
    private static final Pattern POLITE = Pattern.compile("^" + DIRECT + "(?:" + VERBS + ")\\b\\s+");
    private static final Pattern IMPERATIVE = Pattern.compile("^" + VERBS + "\\b\\s+");
    private static final Pattern FYI = Pattern.compile("^fyi\\s*[:,]\\s*");
    private static final Pattern TRAILING_PLEASE = Pattern.compile(",\\s*please[.!?]*$");
    private static final Pattern STATUS_QUESTION = Pattern.compile(
            "^(?:" + DIRECT + ")?(?:check|confirm)\\s+(?:if|whether)\\s+");
    private static final Pattern CANCELED = Pattern.compile(
            "\\b(?:cancel(?:led|ed)|never mind|no need|don't do (?:it|that)|already (?:done|submitted|sent))\\b");
    private static final Pattern DURATION = Pattern.compile("^in\\s+([1-9][0-9]{0,2})\\s+(minute|hour|day)s?\\s*,\\s*");
    private static final Pattern ISO = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern DAY = Pattern.compile("\\b(today|tomorrow|(?:next |this )?(?:monday|tuesday|"
            + "wednesday|thursday|friday|saturday|sunday))\\b");
    private static final Pattern MERIDIEM = Pattern.compile("\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(am|pm)\\b");
    private static final Pattern CLOCK = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern DAYPART_CLOCK = Pattern.compile(
            "\\b(?:at|by|before)\\s+(1[0-2]|0?[1-9])\\s+(?:(?:today|tomorrow)\\s+)?(morning|afternoon|evening)\\b");

    private EnglishTaskRules() {}

    static TaskRules.Match match(String text) {
        if (text.isEmpty() || HAN.matcher(text).find()) return null;
        String[] sentences = SENTENCE.split(text);
        for (int i = 0; i < sentences.length; i++) {
            String clause = sentences[i].strip();
            String lower = clause.toLowerCase(Locale.ROOT).replace('’', '\'');
            Matcher duration = DURATION.matcher(lower);
            if (duration.find() && IMPERATIVE.matcher(lower.substring(duration.end())).find()) {
                if (canceled(sentences, i)) continue;
                String unit = switch (duration.group(2)) {
                    case "minute" -> "分钟";
                    case "hour" -> "小时";
                    default -> "天";
                };
                return new TaskRules.Match(clause, duration.group(1) + unit + "后");
            }

            boolean fyi = FYI.matcher(lower).find();
            String instruction = FYI.matcher(lower).replaceFirst("");
            if (STATUS_QUESTION.matcher(instruction).find()) continue;
            boolean direct = POLITE.matcher(instruction).find();
            if (!direct && !(IMPERATIVE.matcher(instruction).find()
                    && (fyi || TRAILING_PLEASE.matcher(instruction).find() || !instruction.endsWith("?")))) continue;
            if (canceled(sentences, i)) continue;
            String day = date(instruction);
            String clock = time(instruction);
            if (day != null && clock != null) return new TaskRules.Match(clause, day + " " + clock);
        }
        return null;
    }

    private static boolean canceled(String[] sentences, int index) {
        return CANCELED.matcher(sentences[index].toLowerCase(Locale.ROOT).replace('’', '\'')).find()
                || (index + 1 < sentences.length && CANCELED.matcher(
                        sentences[index + 1].toLowerCase(Locale.ROOT).replace('’', '\'')).find());
    }

    private static String date(String text) {
        Matcher iso = ISO.matcher(text);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group()).toString();
            } catch (DateTimeException ignored) {
                return null;
            }
        }
        Matcher day = DAY.matcher(text);
        if (!day.find()) return null;
        String word = day.group(1);
        if (word.equals("today")) return "今天";
        if (word.equals("tomorrow")) return "明天";
        String prefix = word.startsWith("next ") ? "下" : word.startsWith("this ") ? "本" : "";
        String weekday = switch (word.substring(word.lastIndexOf(' ') + 1)) {
            case "monday" -> "一";
            case "tuesday" -> "二";
            case "wednesday" -> "三";
            case "thursday" -> "四";
            case "friday" -> "五";
            case "saturday" -> "六";
            default -> "日";
        };
        return prefix + "周" + weekday;
    }

    private static String time(String text) {
        Matcher meridiem = MERIDIEM.matcher(text);
        if (meridiem.find()) {
            int hour = Integer.parseInt(meridiem.group(1)) % 12 + (meridiem.group(3).equals("pm") ? 12 : 0);
            return clock(hour, meridiem.group(2) == null ? 0 : Integer.parseInt(meridiem.group(2)));
        }
        Matcher clock = CLOCK.matcher(text);
        if (clock.find()) return clock(Integer.parseInt(clock.group(1)), Integer.parseInt(clock.group(2)));
        Matcher daypart = DAYPART_CLOCK.matcher(text);
        if (!daypart.find()) return null;
        int hour = Integer.parseInt(daypart.group(1)) % 12 + (daypart.group(2).equals("morning") ? 0 : 12);
        return clock(hour, 0);
    }

    private static String clock(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }
}
