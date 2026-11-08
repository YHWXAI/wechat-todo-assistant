package com.wechattodo.assistant;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class TaskTime {
    static final long MINUTE = 60_000L;
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm", Locale.SIMPLIFIED_CHINESE);

    private TaskTime() {}

    static String full(long due, ZoneId zone) {
        return FULL.format(Instant.ofEpochMilli(due).atZone(zone));
    }

    static String compact(long due, long now, ZoneId zone) {
        var deadline = Instant.ofEpochMilli(due).atZone(zone);
        int year = Instant.ofEpochMilli(now).atZone(zone).getYear();
        return (deadline.getYear() == year ? SHORT : OTHER_YEAR).format(deadline);
    }

    static String remaining(long due, long now) {
        long delta = due - now;
        if (delta > 0) return "剩余 " + duration((delta - 1) / MINUTE + 1);
        long minutesLate = -delta / MINUTE;
        return minutesLate == 0 ? "已到期" : "已逾期 " + duration(minutesLate);
    }

    static long nextChange(long due, long now) {
        long delta = due - now;
        return delta > 0 ? (delta - 1) % MINUTE + 1 : MINUTE - Math.floorMod(-delta, MINUTE);
    }

    private static String duration(long minutes) {
        long days = minutes / (24 * 60);
        long hours = minutes / 60 % 24;
        long remainder = minutes % 60;
        String dayText = days == 0 ? "" : days + "天";
        String hourText = hours == 0 && days == 0 ? "" : hours + "时";
        return dayText + hourText + remainder + "分";
    }
}
