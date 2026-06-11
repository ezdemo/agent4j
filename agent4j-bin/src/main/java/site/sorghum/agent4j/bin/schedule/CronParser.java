package site.sorghum.agent4j.bin.schedule;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplified Cron expression parser for 6-field format.
 */
public class CronParser {

    private static final Pattern STEP_PATTERN = Pattern.compile("(\\*|\\d+)/(\\d+)");
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)-(\\d+)");
    private static final int MIN_CRON_FIELDS = 6;

    public static long nextTime(String cronExpr, long baseTime) {
        try {
            String[] fields = cronExpr.trim().split("\\s+");
            if (fields.length < MIN_CRON_FIELDS) {
                return -1;
            }

            Set<Integer> seconds = parseField(fields[0], 0, 59);
            Set<Integer> minutes = parseField(fields[1], 0, 59);
            Set<Integer> hours = parseField(fields[2], 0, 23);
            Set<Integer> daysOfMonth = parseField(fields[3], 1, 31);
            Set<Integer> months = parseField(fields[4], 1, 12);
            Set<Integer> daysOfWeek = parseDayOfWeekField(fields[5]);

            LocalDateTime base = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(baseTime),
                    ZoneId.systemDefault());

            LocalDateTime deadline = base.plusYears(2);
            LocalDateTime candidate = base.withSecond(0).withNano(0).plusMinutes(1);

            while (candidate.isBefore(deadline)) {
                if (matches(candidate, seconds, minutes, hours, daysOfMonth, months, daysOfWeek)) {
                    return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
                candidate = candidate.plusMinutes(1);
            }

            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean matches(LocalDateTime dt,
                                   Set<Integer> seconds,
                                   Set<Integer> minutes,
                                   Set<Integer> hours,
                                   Set<Integer> daysOfMonth,
                                   Set<Integer> months,
                                   Set<Integer> daysOfWeek) {
        if (!seconds.contains(dt.getSecond())) return false;
        if (!minutes.contains(dt.getMinute())) return false;
        if (!hours.contains(dt.getHour())) return false;
        if (!months.contains(dt.getMonthValue())) return false;

        boolean domMatch = daysOfMonth.contains(dt.getDayOfMonth());
        boolean dowMatch = daysOfWeek.contains(dt.getDayOfWeek().getValue());

        boolean domWild = daysOfMonth.size() >= 31;
        boolean dowWild = daysOfWeek.size() >= 7;

        if (!domWild && !dowWild) {
            return domMatch && dowMatch;
        } else if (!domWild) {
            return domMatch;
        } else if (!dowWild) {
            return dowMatch;
        }
        return true;
    }

    static Set<Integer> parseField(String field, int min, int max) {
        Set<Integer> result = new HashSet<>();

        if (field.equals("*") || field.equals("?")) {
            for (int i = min; i <= max; i++) result.add(i);
            return result;
        }

        Matcher stepMatcher = STEP_PATTERN.matcher(field);
        if (stepMatcher.matches()) {
            int start = stepMatcher.group(1).equals("*") ? min : Integer.parseInt(stepMatcher.group(1));
            int step = Integer.parseInt(stepMatcher.group(2));
            for (int i = start; i <= max; i += step) {
                result.add(i);
            }
            return result;
        }

        for (String part : field.split(",")) {
            part = part.trim();
            Matcher rangeMatcher = RANGE_PATTERN.matcher(part);
            if (rangeMatcher.matches()) {
                int start = Integer.parseInt(rangeMatcher.group(1));
                int end = Integer.parseInt(rangeMatcher.group(2));
                for (int i = start; i <= end; i++) {
                    if (i >= min && i <= max) result.add(i);
                }
            } else if (part.equals("*") || part.equals("?")) {
                for (int i = min; i <= max; i++) result.add(i);
                return result;
            } else {
                int val = Integer.parseInt(part);
                if (val >= min && val <= max) result.add(val);
            }
        }

        return result;
    }

    static Set<Integer> parseDayOfWeekField(String field) {
        Set<Integer> cronValues = parseField(field, 1, 7);
        Set<Integer> result = new HashSet<>();
        for (int cv : cronValues) {
            if (cv == 1) {
                result.add(7);
            } else {
                result.add(cv - 1);
            }
        }
        return result;
    }
}
