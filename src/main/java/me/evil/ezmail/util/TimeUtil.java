package me.evil.ezmail.util;

import me.evil.ezmail.config.Lang;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间与倒计时的格式化工具。
 */
public final class TimeUtil {

    private static final DateTimeFormatter FALLBACK = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final Map<String, DateTimeFormatter> CACHE = new ConcurrentHashMap<>();

    private TimeUtil() {
    }

    public static String format(long epochMillis, String pattern) {
        DateTimeFormatter formatter = CACHE.computeIfAbsent(pattern == null ? "" : pattern, key -> {
            if (key.isEmpty()) {
                return FALLBACK;
            }
            try {
                return DateTimeFormatter.ofPattern(key).withZone(ZoneId.systemDefault());
            } catch (IllegalArgumentException exception) {
                return FALLBACK;
            }
        });
        return formatter.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * 把剩余毫秒格式化为 "3天 4小时" 这种形式, 最多显示两个单位。
     */
    public static String duration(long millis, Lang lang) {
        if (millis <= 0L) {
            return lang.rawString("countdown-expired");
        }

        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = totalSeconds % 86400L / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long seconds = totalSeconds % 60L;

        List<String> parts = new ArrayList<>(2);
        addPart(parts, lang, "countdown-day", days);
        addPart(parts, lang, "countdown-hour", hours);
        addPart(parts, lang, "countdown-minute", minutes);
        if (parts.isEmpty()) {
            addPart(parts, lang, "countdown-second", seconds);
        }

        return String.join(lang.rawString("countdown-separator"), parts);
    }

    private static void addPart(List<String> parts, Lang lang, String key, long value) {
        if (value > 0L && parts.size() < 2) {
            parts.add(lang.rawString(key).replace("{n}", Long.toString(value)));
        }
    }
}
