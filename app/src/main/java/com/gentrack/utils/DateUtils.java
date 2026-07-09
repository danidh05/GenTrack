package com.gentrack.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DateUtils {

    private static final String FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss";
    private static final String FORMAT_DATE      = "yyyy-MM-dd";
    private static final String FORMAT_MONTH     = "yyyy-MM";

    private DateUtils() {}

    public static String now() {
        return new SimpleDateFormat(FORMAT_DATETIME, Locale.getDefault()).format(new Date());
    }

    public static String today() {
        return new SimpleDateFormat(FORMAT_DATE, Locale.getDefault()).format(new Date());
    }

    public static String currentMonth() {
        return new SimpleDateFormat(FORMAT_MONTH, Locale.getDefault()).format(new Date());
    }

    public static String formatDateTime(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat(FORMAT_DATETIME, Locale.getDefault()).format(date);
    }

    public static boolean isOlderThan(String dateTimeStr, int days) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_DATETIME, Locale.getDefault());
            Date date = sdf.parse(dateTimeStr);
            if (date == null) return false;
            long diffMs = System.currentTimeMillis() - date.getTime();
            return TimeUnit.MILLISECONDS.toDays(diffMs) >= days;
        } catch (Exception e) {
            return false;
        }
    }

    public static String relativeTime(Date date) {
        if (date == null) return "";
        long diffMs = System.currentTimeMillis() - date.getTime();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        long hours   = TimeUnit.MILLISECONDS.toHours(diffMs);
        long days    = TimeUnit.MILLISECONDS.toDays(diffMs);

        if (minutes < 1)  return "just now";
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        if (hours   < 24) return hours   + " hour"   + (hours   == 1 ? "" : "s") + " ago";
        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }
}
