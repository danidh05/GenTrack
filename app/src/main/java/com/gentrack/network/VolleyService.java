package com.gentrack.network;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.MonthlyReport;
import com.gentrack.models.Payment;
import com.gentrack.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class VolleyService {

    private static VolleyService instance;
    private final RequestQueue   requestQueue;
    private final Context        appContext;
    private final SyncManager    syncManager;
    private final RemoteConfigApi remoteConfigApi;
    private final ReportsApi     reportsApi;

    private VolleyService(Context context) {
        appContext      = context.getApplicationContext();
        requestQueue    = Volley.newRequestQueue(appContext);
        syncManager     = new SyncManager(this, appContext);
        remoteConfigApi = new RemoteConfigApi(requestQueue, appContext);
        reportsApi      = new ReportsApi(this, requestQueue, appContext);
    }

    public static synchronized VolleyService getInstance(Context context) {
        if (instance == null) instance = new VolleyService(context);
        return instance;
    }

    // ───── CUSTOMERS ─────

    public void postCustomer(Customer c, ApiCallback cb) {
        firePost(Constants.API_CUSTOMERS_CREATE, RequestBuilder.buildCustomerParams(c),
                Constants.TABLE_CUSTOMERS, "create", cb);
    }

    public void updateCustomer(Customer c, ApiCallback cb) {
        Map<String, String> params = RequestBuilder.buildCustomerParams(c);
        params.put(Constants.COL_ID, String.valueOf(c.getId()));
        firePost(Constants.API_CUSTOMERS_CREATE, params, Constants.TABLE_CUSTOMERS, "update", cb);
    }

    public void deleteCustomer(int id, ApiCallback cb) {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.COL_ID, String.valueOf(id));
        firePost(Constants.API_CUSTOMERS_DELETE, params, Constants.TABLE_CUSTOMERS, "delete", cb);
    }

    // ───── BILLS ─────

    public void postBill(Bill b, ApiCallback cb) {
        Map<String, String> params = RequestBuilder.buildBillParams(b);
        Log.d("GenTrack_BILL", "Posting bill JSON: " + new org.json.JSONObject(params));
        firePost(Constants.API_BILLS_CREATE, params, Constants.TABLE_BILLS, "create", cb);
    }

    public void updateBill(Bill b, ApiCallback cb) {
        Map<String, String> params = RequestBuilder.buildBillParams(b);
        params.put(Constants.COL_ID, String.valueOf(b.getId()));
        firePost(Constants.API_BILLS_CREATE, params, Constants.TABLE_BILLS, "update", cb);
    }

    // ───── PAYMENTS ─────

    public void postPayment(Payment p, ApiCallback cb) {
        Map<String, String> params = RequestBuilder.buildPaymentParams(p);
        Log.d("GenTrack_PAYMENT", "Posting payment JSON: " + new org.json.JSONObject(params));
        firePost(Constants.API_PAYMENTS_CREATE, params, Constants.TABLE_PAYMENTS, "create", cb);
    }

    // ───── CONFIG (delegate) ─────

    public void fetchRemoteConfig(ApiCallback cb) {
        remoteConfigApi.fetchRemoteConfig(cb);
    }

    public void updateRemoteConfig(double price5a, double price10a, double price15a,
                                   double pricePerKwh,
                                   double basePrice5a, double basePrice10a, double basePrice15a,
                                   String currency, ApiCallback cb) {
        remoteConfigApi.updateRemoteConfig(price5a, price10a, price15a, pricePerKwh,
                basePrice5a, basePrice10a, basePrice15a, currency, cb);
    }

    // ───── REPORTS (delegate) ─────

    public void postMonthlyReport(MonthlyReport r, ApiCallback cb) {
        reportsApi.postMonthlyReport(r, cb);
    }

    public void fetchMonthlyReports(ApiCallback cb) {
        reportsApi.fetchMonthlyReports(cb);
    }

    // ───── SYNC QUEUE (delegate) ─────

    public void retrySyncQueue() {
        syncManager.retrySyncQueue();
    }

    // ───── FIRE POST ─────

    void firePost(String url, Map<String, String> params,
                  String tableName, String operation, ApiCallback cb) {
        firePost(url, params, tableName, operation, true, cb);
    }

    void firePost(String url, Map<String, String> params,
                  String tableName, String operation,
                  boolean queueOnFailure, ApiCallback cb) {
        String syncKey = RequestBuilder.buildSyncKey(tableName, operation, params);
        if (!syncManager.inFlightSyncKeys.add(syncKey)) {
            Log.d("GenTrack_SYNC", "Skipping duplicate in-flight sync: " + syncKey);
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            syncManager.inFlightSyncKeys.remove(syncKey);
            if (queueOnFailure) syncManager.addToSyncQueue(tableName, operation, params);
            if (cb != null) cb.onError("Not authenticated");
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(result -> {
                    String token = result.getToken();
                    Log.d("GenTrack", "Token obtained, length: " + (token != null ? token.length() : 0));
                    Map<String, String> postParams = new HashMap<>(params);
                    postParams.put("id_token", token != null ? token : "");
                    final byte[] body = RequestBuilder.encodeJson(postParams);
                    Log.d("GenTrack", "Firing POST to: " + url + " body-bytes=" + body.length);
                    Log.d("GenTrack", "POST params keys: " + postParams.keySet());
                    StringRequest req = new StringRequest(Request.Method.POST, url,
                            response -> {
                                Log.d("GenTrack", "POST response from " + url + ": " + response);
                                try {
                                    JSONObject json = new JSONObject(response);
                                    boolean explicitFailure =
                                            (json.has("success") && !json.optBoolean("success"))
                                                    || "error".equalsIgnoreCase(json.optString("status"));
                                    if (explicitFailure) {
                                        if (cb != null) {
                                            cb.onError(json.optString("message",
                                                    "Server rejected the request."));
                                        }
                                    } else {
                                        syncManager.deleteMatchingQueuedItems(tableName, operation, params);
                                        if (cb != null) cb.onSuccess(json);
                                    }
                                } catch (JSONException e) {
                                    syncManager.deleteMatchingQueuedItems(tableName, operation, params);
                                    if (cb != null) cb.onSuccess(new JSONObject());
                                } finally {
                                    syncManager.inFlightSyncKeys.remove(syncKey);
                                }
                            },
                            error -> {
                                Log.e("GenTrack", "POST failed: " + error.getClass().getSimpleName() + " - " + error.getMessage());
                                boolean permanentClientError = false;
                                if (error.networkResponse != null) {
                                    Log.e("GenTrack", "Response code: " + error.networkResponse.statusCode);
                                    Log.e("GenTrack", "Response body: " + new String(error.networkResponse.data));
                                    permanentClientError = error.networkResponse.statusCode >= 400
                                            && error.networkResponse.statusCode < 500;
                                }
                                if (queueOnFailure && !permanentClientError) {
                                    syncManager.addToSyncQueue(tableName, operation, params);
                                }
                                syncManager.inFlightSyncKeys.remove(syncKey);
                                if (cb != null) cb.onError("Network error. Please try again.");
                            }) {
                        @Override public byte[]  getBody()            { return body; }
                        @Override public String  getBodyContentType() { return "application/json; charset=UTF-8"; }
                    };
                    requestQueue.add(req);
                })
                .addOnFailureListener(e -> {
                    Log.e("GenTrack", "Token error: " + e.getMessage());
                    syncManager.inFlightSyncKeys.remove(syncKey);
                    if (queueOnFailure) syncManager.addToSyncQueue(tableName, operation, params);
                    if (cb != null) cb.onError("Token error: " + e.getMessage());
                });
    }
}
