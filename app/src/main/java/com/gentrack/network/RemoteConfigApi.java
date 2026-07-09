package com.gentrack.network;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;
import com.gentrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

class RemoteConfigApi {

    private final RequestQueue requestQueue;
    private final Context appContext;

    RemoteConfigApi(RequestQueue requestQueue, Context appContext) {
        this.requestQueue = requestQueue;
        this.appContext   = appContext;
    }

    void fetchRemoteConfig(ApiCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (cb != null) cb.onError("Not authenticated");
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(result -> {
                    Map<String, String> p = new HashMap<>();
                    p.put("id_token", result.getToken());
                    final byte[] body = RequestBuilder.encodeJson(p);
                    Log.d("GenTrack", "Firing POST to: " + Constants.API_CONFIG_RATES + " body-bytes=" + body.length);
                    StringRequest req = new StringRequest(Request.Method.POST,
                            Constants.API_CONFIG_RATES,
                            response -> {
                                try {
                                    JSONObject json = new JSONObject(response);
                                    applyRemoteConfigToSession(json);
                                    if (cb != null) cb.onSuccess(json);
                                } catch (JSONException e) {
                                    if (cb != null) cb.onError("Invalid server response.");
                                }
                            },
                            error -> { if (cb != null) cb.onError("Network error. Please try again."); }) {
                        @Override public byte[]  getBody()            { return body; }
                        @Override public String  getBodyContentType() { return "application/json; charset=UTF-8"; }
                    };
                    requestQueue.add(req);
                })
                .addOnFailureListener(e -> {
                    Log.e("GenTrack", "Token error: " + e.getMessage());
                    if (cb != null) cb.onError("Token error: " + e.getMessage());
                });
    }

    void updateRemoteConfig(double price5a, double price10a, double price15a,
                            double pricePerKwh,
                            double basePrice5a, double basePrice10a, double basePrice15a,
                            String currency, ApiCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (cb != null) cb.onError("Not authenticated");
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(result -> {
                    Map<String, String> p = new HashMap<>();
                    p.put("id_token",       result.getToken());
                    p.put("price_5a",       String.valueOf(price5a));
                    p.put("price_10a",      String.valueOf(price10a));
                    p.put("price_15a",      String.valueOf(price15a));
                    p.put("price_per_kwh",  String.valueOf(pricePerKwh));
                    p.put("base_price_5a",  String.valueOf(basePrice5a));
                    p.put("base_price_10a", String.valueOf(basePrice10a));
                    p.put("base_price_15a", String.valueOf(basePrice15a));
                    p.put("currency",       currency);
                    p.put("updated_at",     DateUtils.now());
                    final byte[] body = RequestBuilder.encodeJson(p);
                    Log.d("GenTrack", "Firing POST to: " + Constants.API_CONFIG_UPDATE + " body-bytes=" + body.length);
                    StringRequest req = new StringRequest(Request.Method.POST,
                            Constants.API_CONFIG_UPDATE,
                            response -> {
                                if (cb == null) return;
                                try { cb.onSuccess(new JSONObject(response)); }
                                catch (JSONException e) { cb.onSuccess(new JSONObject()); }
                            },
                            error -> {
                                Log.e("GenTrack", "POST failed: " + error.getClass().getSimpleName() + " - " + error.getMessage());
                                if (error.networkResponse != null) {
                                    Log.e("GenTrack", "Response code: " + error.networkResponse.statusCode);
                                    Log.e("GenTrack", "Response body: " + new String(error.networkResponse.data));
                                }
                                if (cb != null) cb.onError("Network error. Please try again.");
                            }) {
                        @Override public byte[]  getBody()            { return body; }
                        @Override public String  getBodyContentType() { return "application/json; charset=UTF-8"; }
                    };
                    requestQueue.add(req);
                })
                .addOnFailureListener(e -> {
                    Log.e("GenTrack", "Token error: " + e.getMessage());
                    if (cb != null) cb.onError("Token error: " + e.getMessage());
                });
    }

    private void applyRemoteConfigToSession(JSONObject response) {
        JSONObject config = response.optJSONObject("config");
        if (config == null) config = response;

        SessionManager sm = SessionManager.getInstance(appContext);
        double p5   = config.optDouble("price_5a",       0);
        double p10  = config.optDouble("price_10a",      0);
        double p15  = config.optDouble("price_15a",      0);
        double pkw  = config.optDouble("price_per_kwh",  0);
        double bp5  = config.optDouble("base_price_5a",  0);
        double bp10 = config.optDouble("base_price_10a", 0);
        double bp15 = config.optDouble("base_price_15a", 0);

        if (p5   > 0) sm.savePrice5a(p5);
        if (p10  > 0) sm.savePrice10a(p10);
        if (p15  > 0) sm.savePrice15a(p15);
        if (pkw  > 0) sm.savePricePerKwh(pkw);
        if (bp5  > 0) sm.saveBasePrice5a(bp5);
        if (bp10 > 0) sm.saveBasePrice10a(bp10);
        if (bp15 > 0) sm.saveBasePrice15a(bp15);
    }
}
