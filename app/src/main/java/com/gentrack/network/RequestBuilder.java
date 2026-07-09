package com.gentrack.network;

import android.util.Log;

import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.Payment;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class RequestBuilder {

    private RequestBuilder() {}

    static byte[] encodeJson(Map<String, String> params) {
        JSONObject json = new JSONObject(params);
        Log.d("GenTrack_BODY", "Sending JSON: " + json.toString());
        try {
            return json.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }

    static String buildSyncKey(String tableName, String operation, Map<String, String> params) {
        String localId = params.get("local_id");
        if (localId == null || localId.isEmpty()) localId = params.get(Constants.COL_ID);
        if (Constants.TABLE_BILLS.equals(tableName)) {
            if (localId != null && !localId.isEmpty()) {
                return tableName + ":" + operation + ":local:" + localId;
            }
            return tableName + ":" + operation + ":logical:"
                    + params.getOrDefault(Constants.COL_OWNER_UID, "") + ":"
                    + params.getOrDefault("customer_local_id",
                            params.getOrDefault(Constants.COL_CUSTOMER_ID, "")) + ":"
                    + params.getOrDefault(Constants.COL_MONTH, "");
        }
        if (Constants.TABLE_PAYMENTS.equals(tableName)) {
            if (localId != null && !localId.isEmpty()) {
                return tableName + ":" + operation + ":local:" + localId;
            }
            return tableName + ":" + operation + ":logical:"
                    + params.getOrDefault("bill_local_id",
                            params.getOrDefault(Constants.COL_BILL_ID, "")) + ":"
                    + params.getOrDefault(Constants.COL_AMOUNT_PAID, "") + ":"
                    + params.getOrDefault(Constants.COL_DATE, "");
        }
        return tableName + ":" + operation + ":" + String.valueOf(localId);
    }

    static Map<String, String> buildCustomerParams(Customer c) {
        Map<String, String> p = new HashMap<>();
        p.put("local_id",              String.valueOf(c.getId()));
        p.put(Constants.COL_NAME,      c.getName());
        p.put(Constants.COL_PHONE,     c.getPhone()    != null ? c.getPhone()    : "");
        p.put(Constants.COL_LOCATION,  c.getLocation() != null ? c.getLocation() : "");
        p.put(Constants.COL_AMPS,      String.valueOf(c.getAmps()));
        p.put(Constants.COL_STATUS,    c.getStatus());
        p.put(Constants.COL_NOTES,     c.getNotes()    != null ? c.getNotes()    : "");
        p.put(Constants.COL_IMAGE_URL, c.getImageUrl() != null ? c.getImageUrl() : "");
        p.put("created_at",            c.getCreatedAt() != null ? c.getCreatedAt() : "");
        p.put("updated_at",            DateUtils.now());
        return p;
    }

    static Map<String, String> buildBillParams(Bill b) {
        String model = b.getBillingModel() != null
                ? b.getBillingModel() : Constants.BILLING_MODEL_FLAT;

        // tier_fee: fixed base component sent separately from consumption component
        double tierFee = Constants.BILLING_MODEL_BASE_CONSUMPTION.equals(model)
                ? b.getTotal() - b.getConsumption() * b.getPricePerAmp()
                : b.getPricePerAmp();

        Map<String, String> p = new HashMap<>();
        p.put(Constants.COL_OWNER_UID,            b.getOwnerUid());
        p.put(Constants.PARAM_UID,                b.getOwnerUid());
        p.put("local_id",                         String.valueOf(b.getId()));
        p.put("customer_local_id",                String.valueOf(b.getCustomerId()));
        p.put(Constants.COL_CUSTOMER_ID,          String.valueOf(b.getCustomerId()));
        p.put(Constants.COL_MONTH,                b.getMonth());
        p.put(Constants.COL_AMPS,                 String.valueOf(b.getAmps()));
        p.put(Constants.COL_PRICE_PER_AMP,        String.valueOf(b.getPricePerAmp()));
        p.put(Constants.COL_TOTAL,                String.valueOf(b.getTotal()));
        p.put(Constants.COL_PREVIOUS_BALANCE,     String.valueOf(b.getPreviousBalance()));
        p.put(Constants.COL_FINAL_TOTAL,          String.valueOf(b.getFinalTotal()));
        p.put(Constants.COL_STATUS,               b.getStatus());
        p.put(Constants.COL_BILLING_MODEL,        model);
        p.put(Constants.COL_CURRENT_READING,      String.valueOf(b.getCurrentReading()));
        p.put(Constants.COL_PREVIOUS_READING,     String.valueOf(b.getPreviousReading()));
        p.put(Constants.COL_CONSUMPTION,          String.valueOf(b.getConsumption()));
        p.put("tier_fee",                         String.valueOf(tierFee));
        p.put("created_at",                       b.getCreatedAt() != null ? b.getCreatedAt() : "");
        p.put("updated_at",                       DateUtils.now());
        return p;
    }

    static Map<String, String> buildPaymentParams(Payment p) {
        Map<String, String> m = new HashMap<>();
        m.put(Constants.COL_OWNER_UID,            p.getOwnerUid());
        m.put(Constants.PARAM_UID,                p.getOwnerUid());
        m.put("local_id",                         String.valueOf(p.getId()));
        m.put("bill_local_id",                    String.valueOf(p.getBillId()));
        m.put(Constants.COL_BILL_ID,              String.valueOf(p.getBillId()));
        m.put(Constants.COL_AMOUNT_PAID,          String.valueOf(p.getAmountPaid()));
        m.put(Constants.COL_DATE,                 p.getDate());
        m.put(Constants.COL_REMAINING_BALANCE,    String.valueOf(p.getRemainingBalance()));
        m.put("created_at",                       p.getCreatedAt() != null ? p.getCreatedAt() : "");
        m.put("updated_at",                       DateUtils.now());
        return m;
    }
}
