package com.gentrack.network;

import android.content.Context;
import android.util.Log;

import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SyncManager {

    private final VolleyService volleyService;
    private final Context appContext;
    final Set<String> inFlightSyncKeys = Collections.synchronizedSet(new HashSet<>());
    private boolean isRetryingSyncQueue = false;

    SyncManager(VolleyService volleyService, Context appContext) {
        this.volleyService = volleyService;
        this.appContext    = appContext;
    }

    void retrySyncQueue() {
        synchronized (this) {
            if (isRetryingSyncQueue) {
                Log.d("GenTrack_SYNC", "Sync queue retry already running; skipping duplicate trigger.");
                return;
            }
            isRetryingSyncQueue = true;
        }
        DatabaseHandler db = DatabaseHandler.getInstance(appContext);
        db.getAllSyncQueueItems(new DbCallback<List<DatabaseHandler.SyncQueueItem>>() {
            @Override public void onResult(List<DatabaseHandler.SyncQueueItem> items) {
                try {
                    if (items == null) return;
                    for (DatabaseHandler.SyncQueueItem item : items) retryItem(db, item);
                } finally {
                    synchronized (SyncManager.this) {
                        isRetryingSyncQueue = false;
                    }
                }
            }
            @Override public void onError(String e) {
                synchronized (SyncManager.this) {
                    isRetryingSyncQueue = false;
                }
            }
        });
    }

    private void retryItem(DatabaseHandler db, DatabaseHandler.SyncQueueItem item) {
        try {
            JSONObject payload = new JSONObject(item.payloadJson);
            Map<String, String> params = new HashMap<>();
            for (java.util.Iterator<String> it = payload.keys(); it.hasNext(); ) {
                String key = it.next();
                params.put(key, payload.optString(key));
            }
            String url = resolveRetryUrl(item.tableName, item.operation);
            if (url == null) {
                db.deleteSyncQueueItem(item.id, null);
                return;
            }
            normalizeRetryPayload(item.tableName, params);
            volleyService.firePost(url, params, item.tableName, item.operation, false,
                    new ApiCallback() {
                        @Override public void onSuccess(JSONObject r) {
                            db.deleteSyncQueueItem(item.id, null);
                        }
                        @Override public void onError(String e) {
                            /* leave in queue, retry next launch */
                        }
                    });
        } catch (JSONException e) {
            db.deleteSyncQueueItem(item.id, null);
        }
    }

    private void normalizeRetryPayload(String tableName, Map<String, String> params) {
        if (Constants.TABLE_BILLS.equals(tableName)
                && !params.containsKey("customer_local_id")
                && params.containsKey(Constants.COL_CUSTOMER_ID)) {
            params.put("customer_local_id", params.get(Constants.COL_CUSTOMER_ID));
        }
        if (Constants.TABLE_PAYMENTS.equals(tableName)
                && !params.containsKey("bill_local_id")
                && params.containsKey(Constants.COL_BILL_ID)) {
            params.put("bill_local_id", params.get(Constants.COL_BILL_ID));
        }
    }

    private String resolveRetryUrl(String tableName, String operation) {
        switch (tableName) {
            case Constants.TABLE_CUSTOMERS:
                if ("create".equals(operation)) return Constants.API_CUSTOMERS_CREATE;
                if ("update".equals(operation)) return Constants.API_CUSTOMERS_CREATE;
                if ("delete".equals(operation)) return Constants.API_CUSTOMERS_DELETE;
                break;
            case Constants.TABLE_BILLS:
                if ("create".equals(operation)) return Constants.API_BILLS_CREATE;
                if ("update".equals(operation)) return Constants.API_BILLS_CREATE;
                break;
            case Constants.TABLE_PAYMENTS:
                if ("create".equals(operation)) return Constants.API_PAYMENTS_CREATE;
                break;
            case "reports":
                if ("create".equals(operation)) return Constants.API_REPORTS_SAVE;
                break;
        }
        return null;
    }

    void addToSyncQueue(String tableName, String operation, Map<String, String> params) {
        JSONObject payload = new JSONObject(params);
        DatabaseHandler.getInstance(appContext)
                .insertSyncQueueItem(tableName, operation, payload.toString(), null);
    }

    void deleteMatchingQueuedItems(String tableName, String operation, Map<String, String> params) {
        JSONObject payload = new JSONObject(params);
        DatabaseHandler.getInstance(appContext)
                .deleteMatchingSyncQueueItems(tableName, operation, payload.toString(), null);
    }
}
