package com.gentrack.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.gentrack.models.Customer;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CustomerDao {

    long insert(SQLiteDatabase db, Customer c) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_OWNER_UID,  c.getOwnerUid());
        cv.put(Constants.COL_NAME,       c.getName());
        cv.put(Constants.COL_PHONE,      c.getPhone());
        cv.put(Constants.COL_LOCATION,   c.getLocation());
        cv.put(Constants.COL_AMPS,       c.getAmps());
        cv.put(Constants.COL_STATUS,     c.getStatus());
        cv.put(Constants.COL_NOTES,      c.getNotes());
        cv.put(Constants.COL_IMAGE_URL,  c.getImageUrl());
        cv.put(Constants.COL_CREATED_AT, DateUtils.now());
        cv.put(Constants.COL_UPDATED_AT, DateUtils.now());
        return db.insert(Constants.TABLE_CUSTOMERS, null, cv);
    }

    int update(SQLiteDatabase db, Customer c) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_NAME,       c.getName());
        cv.put(Constants.COL_PHONE,      c.getPhone());
        cv.put(Constants.COL_LOCATION,   c.getLocation());
        cv.put(Constants.COL_AMPS,       c.getAmps());
        cv.put(Constants.COL_STATUS,     c.getStatus());
        cv.put(Constants.COL_NOTES,      c.getNotes());
        cv.put(Constants.COL_IMAGE_URL,  c.getImageUrl());
        cv.put(Constants.COL_UPDATED_AT, DateUtils.now());
        return db.update(Constants.TABLE_CUSTOMERS, cv,
                Constants.COL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(c.getId()), c.getOwnerUid()});
    }

    void updateStatus(SQLiteDatabase db, int customerId, String status, String ownerUid) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_STATUS,     status);
        cv.put(Constants.COL_UPDATED_AT, DateUtils.now());
        db.update(Constants.TABLE_CUSTOMERS, cv,
                Constants.COL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(customerId), ownerUid});
    }

    boolean delete(SQLiteDatabase db, int id, String ownerUid) {
        db.delete(Constants.TABLE_CUSTOMERS,
                Constants.COL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(id), ownerUid});
        return true;
    }

    List<Customer> getAll(SQLiteDatabase db, String ownerUid) {
        List<Customer> list = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_CUSTOMERS, null,
                Constants.COL_OWNER_UID + "=?", new String[]{ownerUid},
                null, null, Constants.COL_NAME + " ASC");
        try {
            if (c.moveToFirst()) {
                do { list.add(fromCursor(c)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return list;
    }

    Customer getById(SQLiteDatabase db, int id, String ownerUid) {
        Cursor c = db.query(Constants.TABLE_CUSTOMERS, null,
                Constants.COL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(id), ownerUid}, null, null, null);
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally {
            c.close();
        }
    }

    List<Customer> getByStatus(SQLiteDatabase db, String status, String ownerUid) {
        List<Customer> list = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_CUSTOMERS, null,
                Constants.COL_OWNER_UID + "=? AND " + Constants.COL_STATUS + "=?",
                new String[]{ownerUid, status}, null, null, null);
        try {
            if (c.moveToFirst()) {
                do { list.add(fromCursor(c)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return list;
    }

    int countByStatus(SQLiteDatabase db, String status, String ownerUid) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + Constants.TABLE_CUSTOMERS
                + " WHERE " + Constants.COL_OWNER_UID + "=? AND " + Constants.COL_STATUS + "=?",
                new String[]{ownerUid, status});
        try {
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            c.close();
        }
    }

    Map<String, Integer> getStatusCounts(SQLiteDatabase db, String ownerUid) {
        Map<String, Integer> map = new HashMap<>();
        Cursor c = db.rawQuery(
                "SELECT " + Constants.COL_STATUS + ", COUNT(*) FROM " + Constants.TABLE_CUSTOMERS
                + " WHERE " + Constants.COL_OWNER_UID + "=?"
                + " GROUP BY " + Constants.COL_STATUS,
                new String[]{ownerUid});
        try {
            if (c.moveToFirst()) {
                do { map.put(c.getString(0), c.getInt(1)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return map;
    }

    private Customer fromCursor(Cursor c) {
        return new Customer(
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_ID)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_OWNER_UID)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_NAME)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_PHONE)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_LOCATION)),
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_AMPS)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_STATUS)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_NOTES)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_IMAGE_URL)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_CREATED_AT)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_UPDATED_AT))
        );
    }
}
