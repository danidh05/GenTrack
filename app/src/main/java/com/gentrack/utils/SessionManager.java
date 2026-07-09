package com.gentrack.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveSession(String uid, String email) {
        prefs.edit()
             .putString(Constants.PREF_UID, uid)
             .putString(Constants.PREF_EMAIL, email)
             .apply();
    }

    public String getUid() {
        return prefs.getString(Constants.PREF_UID, "");
    }

    public String getEmail() {
        return prefs.getString(Constants.PREF_EMAIL, "");
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return !getUid().isEmpty();
    }

    public void saveDefaultPricePerAmp(double price) {
        prefs.edit().putString(Constants.PREF_DEFAULT_PRICE_PER_AMP, String.valueOf(price)).apply();
    }

    public double getDefaultPricePerAmp() {
        return getDoublePrice(Constants.PREF_DEFAULT_PRICE_PER_AMP);
    }

    public void savePrice5a(double price) {
        prefs.edit().putString(Constants.PREF_PRICE_5A, String.valueOf(price)).apply();
    }

    public double getPrice5a() {
        return getDoublePrice(Constants.PREF_PRICE_5A);
    }

    public void savePrice10a(double price) {
        prefs.edit().putString(Constants.PREF_PRICE_10A, String.valueOf(price)).apply();
    }

    public double getPrice10a() {
        return getDoublePrice(Constants.PREF_PRICE_10A);
    }

    public void savePrice15a(double price) {
        prefs.edit().putString(Constants.PREF_PRICE_15A, String.valueOf(price)).apply();
    }

    public double getPrice15a() {
        return getDoublePrice(Constants.PREF_PRICE_15A);
    }

    public void savePricePerKwh(double price) {
        prefs.edit().putString(Constants.PREF_PRICE_PER_KWH, String.valueOf(price)).apply();
    }

    public double getPricePerKwh() {
        return getDoublePrice(Constants.PREF_PRICE_PER_KWH);
    }

    public void saveBasePrice5a(double price) {
        prefs.edit().putString(Constants.PREF_BASE_PRICE_5A, String.valueOf(price)).apply();
    }

    public double getBasePrice5a() {
        return getDoublePrice(Constants.PREF_BASE_PRICE_5A);
    }

    public void saveBasePrice10a(double price) {
        prefs.edit().putString(Constants.PREF_BASE_PRICE_10A, String.valueOf(price)).apply();
    }

    public double getBasePrice10a() {
        return getDoublePrice(Constants.PREF_BASE_PRICE_10A);
    }

    public void saveBasePrice15a(double price) {
        prefs.edit().putString(Constants.PREF_BASE_PRICE_15A, String.valueOf(price)).apply();
    }

    public double getBasePrice15a() {
        return getDoublePrice(Constants.PREF_BASE_PRICE_15A);
    }

    // Reads a price stored as String (new format) with fallback for legacy float-stored values.
    private double getDoublePrice(String key) {
        try {
            String v = prefs.getString(key, "0");
            return Double.parseDouble(v != null ? v : "0");
        } catch (ClassCastException legacy) {
            // Value was saved as float before migration — read it and re-save as String.
            double v = prefs.getFloat(key, 0f);
            prefs.edit().remove(key).putString(key, String.valueOf(v)).apply();
            return v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void saveCurrency(String currency) {
        prefs.edit().putString(Constants.PREF_CURRENCY, currency).apply();
    }

    public String getCurrency() {
        return prefs.getString(Constants.PREF_CURRENCY, "USD");
    }
}
