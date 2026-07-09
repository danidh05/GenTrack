package com.gentrack.utils;

public final class ValidationUtils {

    private ValidationUtils() {}

    public static String validateCustomerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty.";
        }
        return null;
    }

    public static String validateAmps(String ampsStr) {
        if (ampsStr == null || ampsStr.trim().isEmpty()) {
            return "Amps is required.";
        }
        try {
            int amps = Integer.parseInt(ampsStr.trim());
            if (amps <= 0) return "Amps must be a positive number.";
        } catch (NumberFormatException e) {
            return "Amps must be a valid integer.";
        }
        return null;
    }

    public static String validatePricePerAmp(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return "Price per amp is required.";
        }
        try {
            double price = Double.parseDouble(priceStr.trim());
            if (price <= 0) return "Price per amp must be positive.";
        } catch (NumberFormatException e) {
            return "Price per amp must be a valid number.";
        }
        return null;
    }

    public static String validatePaymentAmount(String amountStr, double remainingBalance) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return "Payment amount is required.";
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr.trim());
        } catch (NumberFormatException e) {
            return "Payment amount must be a valid number.";
        }
        if (amount <= 0) {
            return "Payment amount must be positive.";
        }
        if (Math.round(amount * 100) > Math.round(remainingBalance * 100)) {
            return String.format("Payment cannot exceed remaining balance of $%.2f.",
                    Math.round(remainingBalance * 100) / 100.0);
        }
        return null;
    }
}
