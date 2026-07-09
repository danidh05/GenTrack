package com.gentrack.services;

import android.content.Context;

import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.MonthlyReport;
import com.gentrack.network.ApiCallback;
import com.gentrack.network.VolleyService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ReportService {

    private static ReportService instance;

    private final Context appContext;
    private final DatabaseHandler db;
    private final VolleyService volley;

    private ReportService(Context context) {
        appContext = context.getApplicationContext();
        db = DatabaseHandler.getInstance(appContext);
        volley = VolleyService.getInstance(appContext);
    }

    public static synchronized ReportService getInstance(Context context) {
        if (instance == null) instance = new ReportService(context);
        return instance;
    }

    public void getMonthlyRevenueReports(String ownerUid, int limit,
                                         DbCallback<List<MonthlyReport>> cb) {
        volley.fetchMonthlyReports(new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                List<MonthlyReport> reports = parseMonthlyReports(response, ownerUid);
                if (reports.isEmpty()) {
                    loadLocalFallback(ownerUid, limit, cb);
                } else if (cb != null) {
                    cb.onResult(reports);
                }
            }

            @Override public void onError(String e) {
                loadLocalFallback(ownerUid, limit, cb);
            }
        });
    }

    private void loadLocalFallback(String ownerUid, int limit,
                                   DbCallback<List<MonthlyReport>> cb) {
        db.getMonthlyRevenueReports(ownerUid, limit, new DbCallback<List<MonthlyReport>>() {
            @Override public void onResult(List<MonthlyReport> result) {
                if (cb != null) cb.onResult(reverse(result));
            }

            @Override public void onError(String e) {
                if (cb != null) cb.onError(e);
            }
        });
    }

    private List<MonthlyReport> parseMonthlyReports(JSONObject response, String ownerUid) {
        JSONArray arr = response.optJSONArray("reports");
        if (arr == null) arr = response.optJSONArray("data");
        if (arr == null) arr = response.optJSONArray("monthly_reports");

        List<MonthlyReport> reports = new ArrayList<>();
        if (arr == null) return reports;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            String month = obj.optString("month", "");
            if (month.isEmpty()) continue;
            reports.add(new MonthlyReport(
                    month,
                    obj.optInt("total_customers_billed", obj.optInt("bill_count", 0)),
                    readRevenue(obj),
                    obj.optString("owner_uid", ownerUid)));
        }
        return reports;
    }

    private double readRevenue(JSONObject obj) {
        if (obj.has("total_expected_revenue")) return obj.optDouble("total_expected_revenue", 0);
        if (obj.has("expected_revenue")) return obj.optDouble("expected_revenue", 0);
        if (obj.has("total_revenue")) return obj.optDouble("total_revenue", 0);
        if (obj.has("revenue")) return obj.optDouble("revenue", 0);
        return obj.optDouble("amount", 0);
    }

    private List<MonthlyReport> reverse(List<MonthlyReport> reports) {
        List<MonthlyReport> reversed = new ArrayList<>();
        if (reports == null) return reversed;
        for (int i = reports.size() - 1; i >= 0; i--) {
            reversed.add(reports.get(i));
        }
        return reversed;
    }
}
