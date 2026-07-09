package com.gentrack.network;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.gentrack.models.MonthlyReport;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;
import com.gentrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

class ReportsApi {

    private final VolleyService volleyService;
    private final RequestQueue requestQueue;
    private final Context appContext;

    ReportsApi(VolleyService volleyService, RequestQueue requestQueue, Context appContext) {
        this.volleyService = volleyService;
        this.requestQueue  = requestQueue;
        this.appContext    = appContext;
    }

    void postMonthlyReport(MonthlyReport r, ApiCallback cb) {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.COL_OWNER_UID,         r.getOwnerUid());
        params.put(Constants.PARAM_UID,             r.getOwnerUid());
        params.put(Constants.COL_MONTH,             r.getMonth());
        params.put("total_customers_billed",        String.valueOf(r.getTotalCustomersBilled()));
        params.put("total_expected_revenue",        String.valueOf(r.getTotalExpectedRevenue()));
        params.put("created_at",                    DateUtils.now());
        Log.d("GenTrack_REPORT", "Posting monthly report JSON: " + new JSONObject(params));
        volleyService.firePost(Constants.API_REPORTS_SAVE, params, "reports", "create", cb);
    }

    void fetchMonthlyReports(ApiCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (cb != null) cb.onError("Not authenticated");
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(result -> {
                    Map<String, String> p = new HashMap<>();
                    p.put("id_token",              result.getToken());
                    String ownerUid = SessionManager.getInstance(appContext).getUid();
                    p.put(Constants.COL_OWNER_UID, ownerUid);
                    p.put(Constants.PARAM_UID,     ownerUid);
                    p.put(Constants.PARAM_LIMIT,   "3");
                    final byte[] body = RequestBuilder.encodeJson(p);
                    Log.d("GenTrack", "Firing POST to: " + Constants.API_REPORTS_MONTHLY + " body-bytes=" + body.length);
                    StringRequest req = new StringRequest(Request.Method.POST,
                            Constants.API_REPORTS_MONTHLY,
                            response -> {
                                if (cb == null) return;
                                try { cb.onSuccess(new JSONObject(response)); }
                                catch (JSONException e) { cb.onError("Invalid server response."); }
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
}
