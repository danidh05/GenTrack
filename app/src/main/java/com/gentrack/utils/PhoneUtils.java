package com.gentrack.utils;

public final class PhoneUtils {

    private static final String DEFAULT_COUNTRY_CODE = Constants.DEFAULT_COUNTRY_CODE;

    private PhoneUtils() {}

    public static String toInternational(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        String trimmed = phone.trim();
        String digits  = trimmed.replaceAll("[\\s\\-().+]", "");
        if (trimmed.startsWith("+")) {
            return "+" + digits;
        } else if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        } else {
            return DEFAULT_COUNTRY_CODE + digits;
        }
    }

    public static String formatDisplay(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        return phone.trim();
    }
}
