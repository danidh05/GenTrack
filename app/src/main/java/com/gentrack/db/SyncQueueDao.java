package com.gentrack.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class SyncQueueDao {

    void insert(SQLiteDatabase db, String tableName, String operation, String payloadJson) {
        // Payloads are full-record snapshots, not partial diffs, so a newer snapshot for the
        // same entity always supersedes an older queued one. Coalesce into the existing row
        // instead of dropping the new payload, otherwise a second offline edit (e.g. amps)
        // made before the first (e.g. name) has synced is silently lost from the remote sync.
        List<Integer> existingIds = matchingIds(db, tableName, operation, payloadJson);
        if (!existingIds.isEmpty()) {
            ContentValues cv = new ContentValues();
            cv.put(Constants.COL_PAYLOAD_JSON, payloadJson);
            for (Integer id : existingIds) {
                db.update(Constants.TABLE_SYNC_QUEUE, cv,
                        Constants.COL_ID + "=?", new String[]{String.valueOf(id)});
            }
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_TABLE_NAME,   tableName);
        cv.put(Constants.COL_OPERATION,    operation);
        cv.put(Constants.COL_PAYLOAD_JSON, payloadJson);
        cv.put(Constants.COL_CREATED_AT,   DateUtils.now());
        db.insert(Constants.TABLE_SYNC_QUEUE, null, cv);
    }

    List<DatabaseHandler.SyncQueueItem> getAll(SQLiteDatabase db) {
        List<DatabaseHandler.SyncQueueItem> list = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_SYNC_QUEUE, null,
                null, null, null, null, Constants.COL_CREATED_AT + " ASC");
        try {
            if (c.moveToFirst()) {
                do {
                    list.add(new DatabaseHandler.SyncQueueItem(
                            c.getInt(c.getColumnIndexOrThrow(Constants.COL_ID)),
                            c.getString(c.getColumnIndexOrThrow(Constants.COL_TABLE_NAME)),
                            c.getString(c.getColumnIndexOrThrow(Constants.COL_OPERATION)),
                            c.getString(c.getColumnIndexOrThrow(Constants.COL_PAYLOAD_JSON))
                    ));
                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return list;
    }

    void delete(SQLiteDatabase db, int id) {
        db.delete(Constants.TABLE_SYNC_QUEUE, Constants.COL_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    void deleteMatching(SQLiteDatabase db, String tableName, String operation, String payloadJson) {
        List<Integer> ids = matchingIds(db, tableName, operation, payloadJson);
        for (Integer id : ids) {
            delete(db, id);
        }
    }

    private List<Integer> matchingIds(SQLiteDatabase db, String tableName,
                                      String operation, String payloadJson) {
        List<Integer> ids = new ArrayList<>();
        JSONObject incoming = parse(payloadJson);
        Cursor c = db.query(Constants.TABLE_SYNC_QUEUE, null,
                Constants.COL_TABLE_NAME + "=? AND " + Constants.COL_OPERATION + "=?",
                new String[]{tableName, operation}, null, null, null);
        try {
            if (c.moveToFirst()) {
                do {
                    String existingPayload =
                            c.getString(c.getColumnIndexOrThrow(Constants.COL_PAYLOAD_JSON));
                    JSONObject existing = parse(existingPayload);
                    if (sameSyncEntity(tableName, incoming, existing)) {
                        ids.add(c.getInt(c.getColumnIndexOrThrow(Constants.COL_ID)));
                    }
                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return ids;
    }

    private JSONObject parse(String payloadJson) {
        try {
            return new JSONObject(payloadJson);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private boolean sameSyncEntity(String tableName, JSONObject a, JSONObject b) {
        if (Constants.TABLE_BILLS.equals(tableName)) {
            if (sameNonEmpty(a, b, "local_id")) return true;
            return sameNonEmpty(a, b, Constants.COL_OWNER_UID)
                    && sameBillCustomer(a, b)
                    && sameNonEmpty(a, b, Constants.COL_MONTH);
        }
        if (Constants.TABLE_PAYMENTS.equals(tableName)) {
            if (sameNonEmpty(a, b, "local_id")) return true;
            return sameBillId(a, b)
                    && sameNonEmpty(a, b, Constants.COL_AMOUNT_PAID)
                    && sameNonEmpty(a, b, Constants.COL_DATE);
        }
        if (Constants.TABLE_CUSTOMERS.equals(tableName)) {
            return sameNonEmpty(a, b, "local_id") || sameNonEmpty(a, b, Constants.COL_ID);
        }
        return a.toString().equals(b.toString());
    }

    private boolean sameBillCustomer(JSONObject a, JSONObject b) {
        String aCustomer = firstNonEmpty(a, "customer_local_id", Constants.COL_CUSTOMER_ID);
        String bCustomer = firstNonEmpty(b, "customer_local_id", Constants.COL_CUSTOMER_ID);
        return !aCustomer.isEmpty() && aCustomer.equals(bCustomer);
    }

    private boolean sameBillId(JSONObject a, JSONObject b) {
        String aBill = firstNonEmpty(a, "bill_local_id", Constants.COL_BILL_ID);
        String bBill = firstNonEmpty(b, "bill_local_id", Constants.COL_BILL_ID);
        return !aBill.isEmpty() && aBill.equals(bBill);
    }

    private boolean sameNonEmpty(JSONObject a, JSONObject b, String key) {
        String av = a.optString(key, "");
        String bv = b.optString(key, "");
        return !av.isEmpty() && av.equals(bv);
    }

    private String firstNonEmpty(JSONObject json, String firstKey, String secondKey) {
        String first = json.optString(firstKey, "");
        return !first.isEmpty() ? first : json.optString(secondKey, "");
    }
}
