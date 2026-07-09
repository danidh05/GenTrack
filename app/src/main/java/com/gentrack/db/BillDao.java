package com.gentrack.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.MonthlyReport;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;

class BillDao {

    long insert(SQLiteDatabase db, Bill b) {
        Bill existing = getByCustomerAndMonth(db, b.getCustomerId(), b.getOwnerUid(), b.getMonth());
        if (existing != null) {
            return -2L;
        }
        ContentValues cv = buildContentValues(b);
        return db.insertWithOnConflict(Constants.TABLE_BILLS, null, cv,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    int update(SQLiteDatabase db, Bill b) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_STATUS,     b.getStatus());
        cv.put(Constants.COL_UPDATED_AT, DateUtils.now());
        return db.update(Constants.TABLE_BILLS, cv,
                Constants.COL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(b.getId()), b.getOwnerUid()});
    }

    Bill getById(SQLiteDatabase db, int id, String ownerUid) {
        Cursor c = db.query(Constants.TABLE_BILLS, null,
                Constants.COL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(id), ownerUid}, null, null, null);
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally {
            c.close();
        }
    }

    Bill getByCustomerAndMonth(SQLiteDatabase db, int customerId, String ownerUid, String month) {
        Cursor c = db.query(Constants.TABLE_BILLS, null,
                Constants.COL_CUSTOMER_ID + "=? AND "
                        + Constants.COL_OWNER_UID + "=? AND "
                        + Constants.COL_MONTH + "=?",
                new String[]{String.valueOf(customerId), ownerUid, month},
                null, null, Constants.COL_ID + " ASC", "1");
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally {
            c.close();
        }
    }

    List<Bill> getAllForCustomer(SQLiteDatabase db, int customerId, String ownerUid) {
        List<Bill> list = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_BILLS, null,
                Constants.COL_CUSTOMER_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(customerId), ownerUid},
                null, null, Constants.COL_CREATED_AT + " DESC");
        try {
            if (c.moveToFirst()) {
                do { list.add(fromCursor(c)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Same as getAllForCustomer but also sums payments per bill via LEFT JOIN. */
    List<Bill> getAllForCustomerWithPaidAmounts(SQLiteDatabase db, int customerId, String ownerUid) {
        List<Bill> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT b.*, COALESCE(SUM(p." + Constants.COL_AMOUNT_PAID + "), 0) AS total_paid"
                + " FROM " + Constants.TABLE_BILLS + " b"
                + " LEFT JOIN " + Constants.TABLE_PAYMENTS + " p"
                + " ON b." + Constants.COL_ID + " = p." + Constants.COL_BILL_ID
                + " AND p." + Constants.COL_OWNER_UID + " = b." + Constants.COL_OWNER_UID
                + " WHERE b." + Constants.COL_CUSTOMER_ID + " = ?"
                + " AND b." + Constants.COL_OWNER_UID + " = ?"
                + " GROUP BY b." + Constants.COL_ID
                + " ORDER BY b." + Constants.COL_CREATED_AT + " DESC",
                new String[]{String.valueOf(customerId), ownerUid});
        try {
            if (c.moveToFirst()) {
                do {
                    Bill bill = fromCursor(c);
                    int idx = c.getColumnIndex("total_paid");
                    if (idx >= 0) bill.setTransientTotalPaid(c.getDouble(idx));
                    list.add(bill);
                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return list;
    }

    List<String> getStatusesForCustomer(SQLiteDatabase db, int customerId, String ownerUid) {
        List<String> statuses = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_BILLS,
                new String[]{Constants.COL_STATUS},
                Constants.COL_CUSTOMER_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(customerId), ownerUid}, null, null, null);
        try {
            if (c.moveToFirst()) {
                do { statuses.add(c.getString(0)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return statuses;
    }

    /**
     * Returns the outstanding balance for a customer from bills strictly before {@code beforeMonth}.
     * Only Unpaid/Partial bills are counted. Bills with month >= beforeMonth are excluded so that
     * generating a new bill never picks up debt from the same or a later period.
     */
    double getPreviousBalance(SQLiteDatabase db, int customerId, String ownerUid,
                              String beforeMonth) {
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(b." + Constants.COL_FINAL_TOTAL + "), 0)"
                + " - COALESCE(SUM(p." + Constants.COL_AMOUNT_PAID + "), 0)"
                + " FROM " + Constants.TABLE_BILLS + " b"
                + " LEFT JOIN " + Constants.TABLE_PAYMENTS + " p"
                + " ON b." + Constants.COL_ID + " = p." + Constants.COL_BILL_ID
                + " WHERE b." + Constants.COL_CUSTOMER_ID + "=?"
                + "   AND b." + Constants.COL_OWNER_UID   + "=?"
                + "   AND b." + Constants.COL_STATUS + " IN ('"
                + Constants.STATUS_BILL_UNPAID + "','" + Constants.STATUS_PARTIAL + "')"
                + "   AND b." + Constants.COL_MONTH + " < ?",
                new String[]{String.valueOf(customerId), ownerUid, beforeMonth});
        try {
            if (c.moveToFirst()) return Math.max(0, c.getDouble(0));
            return 0;
        } finally {
            c.close();
        }
    }

    /**
     * Marks all Unpaid/Partial bills for this customer with month < beforeMonth as Paid.
     * Called after a bill that carried a previous_balance is fully paid — the prior debts
     * were included in that payment so they should be closed automatically.
     * Returns the list of bills that were closed (with status already set to Paid).
     */
    List<Bill> closePriorBills(SQLiteDatabase db, int customerId, String ownerUid,
                               String beforeMonth) {
        String where = Constants.COL_CUSTOMER_ID + "=? AND " + Constants.COL_OWNER_UID + "=?"
                + " AND " + Constants.COL_STATUS + " IN ('"
                + Constants.STATUS_BILL_UNPAID + "','" + Constants.STATUS_PARTIAL + "')"
                + " AND " + Constants.COL_MONTH + " < ?";
        String[] args = {String.valueOf(customerId), ownerUid, beforeMonth};

        List<Bill> prior = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_BILLS, null, where, args, null, null, null);
        try {
            if (c.moveToFirst()) {
                do { prior.add(fromCursor(c)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        if (prior.isEmpty()) return prior;

        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_STATUS, Constants.STATUS_PAID);
        cv.put(Constants.COL_UPDATED_AT, DateUtils.now());
        db.update(Constants.TABLE_BILLS, cv, where, args);

        for (Bill b : prior) b.setStatus(Constants.STATUS_PAID);
        return prior;
    }

    boolean hasBillsForCustomer(SQLiteDatabase db, int customerId, String ownerUid) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + Constants.TABLE_BILLS
                + " WHERE " + Constants.COL_CUSTOMER_ID + "=?"
                + "   AND " + Constants.COL_OWNER_UID   + "=?",
                new String[]{String.valueOf(customerId), ownerUid});
        try {
            if (c.moveToFirst()) return c.getInt(0) > 0;
            return false;
        } finally {
            c.close();
        }
    }

    /**
     * Returns total outstanding (final_total minus payments) for all Unpaid/Partial bills for
     * this owner.
     */
    double getTotalOutstanding(SQLiteDatabase db, String ownerUid) {
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(b." + Constants.COL_FINAL_TOTAL + "), 0)"
                + " - COALESCE(SUM(p." + Constants.COL_AMOUNT_PAID + "), 0)"
                + " FROM " + Constants.TABLE_BILLS + " b"
                + " LEFT JOIN " + Constants.TABLE_PAYMENTS + " p"
                + " ON b." + Constants.COL_ID + " = p." + Constants.COL_BILL_ID
                + " WHERE b." + Constants.COL_OWNER_UID + "=?"
                + "   AND b." + Constants.COL_STATUS + " IN ('"
                + Constants.STATUS_BILL_UNPAID + "','" + Constants.STATUS_PARTIAL + "')",
                new String[]{ownerUid});
        try {
            if (c.moveToFirst()) return Math.max(0, c.getDouble(0));
            return 0;
        } finally {
            c.close();
        }
    }

    List<MonthlyReport> getMonthlyRevenueReports(SQLiteDatabase db, String ownerUid, int limit) {
        List<MonthlyReport> reports = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT " + Constants.COL_MONTH + ","
                + " COUNT(*) AS bill_count,"
                + " COALESCE(SUM(" + Constants.COL_FINAL_TOTAL + "), 0) AS revenue"
                + " FROM " + Constants.TABLE_BILLS
                + " WHERE " + Constants.COL_OWNER_UID + "=?"
                + " GROUP BY " + Constants.COL_MONTH
                + " ORDER BY " + Constants.COL_MONTH + " DESC"
                + " LIMIT ?",
                new String[]{ownerUid, String.valueOf(limit)});
        try {
            if (c.moveToFirst()) {
                do {
                    reports.add(new MonthlyReport(
                            c.getString(c.getColumnIndexOrThrow(Constants.COL_MONTH)),
                            c.getInt(c.getColumnIndexOrThrow("bill_count")),
                            c.getDouble(c.getColumnIndexOrThrow("revenue")),
                            ownerUid));
                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return reports;
    }

    List<Bill> getUnpaidOlderThan(SQLiteDatabase db, int days, String ownerUid) {
        List<Bill> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + Constants.TABLE_BILLS
                + " WHERE " + Constants.COL_OWNER_UID + "=?"
                + "   AND " + Constants.COL_STATUS + "='" + Constants.STATUS_BILL_UNPAID + "'"
                + "   AND datetime(" + Constants.COL_CREATED_AT + ") < datetime('now', '-"
                + days + " days')",
                new String[]{ownerUid});
        try {
            if (c.moveToFirst()) {
                do { list.add(fromCursor(c)); } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Inserts a list of bills inside a single SQLite transaction. Returns inserted bills with IDs set. */
    List<Bill> insertBatch(SQLiteDatabase db, List<Bill> bills) {
        List<Bill> inserted = new ArrayList<>();
        db.beginTransaction();
        try {
            for (Bill b : bills) {
                Bill existing = getByCustomerAndMonth(db, b.getCustomerId(),
                        b.getOwnerUid(), b.getMonth());
                if (existing != null) {
                    continue;
                }
                long id = db.insertWithOnConflict(Constants.TABLE_BILLS, null,
                        buildContentValues(b), SQLiteDatabase.CONFLICT_IGNORE);
                if (id != -1) {
                    b.setId((int) id);
                    inserted.add(b);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return inserted;
    }

    /** SQL SUM of payments for a bill — avoids fetching the full list into memory. */
    double getSumPayments(SQLiteDatabase db, int billId, String ownerUid) {
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(" + Constants.COL_AMOUNT_PAID + "), 0)"
                + " FROM " + Constants.TABLE_PAYMENTS
                + " WHERE " + Constants.COL_BILL_ID   + "=?"
                + "   AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(billId), ownerUid});
        try {
            if (c.moveToFirst()) return c.getDouble(0);
            return 0;
        } finally {
            c.close();
        }
    }

    ContentValues buildContentValues(Bill b) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_OWNER_UID,        b.getOwnerUid());
        cv.put(Constants.COL_CUSTOMER_ID,      b.getCustomerId());
        cv.put(Constants.COL_MONTH,            b.getMonth());
        cv.put(Constants.COL_AMPS,             b.getAmps());
        cv.put(Constants.COL_PRICE_PER_AMP,    b.getPricePerAmp());
        cv.put(Constants.COL_TOTAL,            b.getTotal());
        cv.put(Constants.COL_PREVIOUS_BALANCE, b.getPreviousBalance());
        cv.put(Constants.COL_FINAL_TOTAL,      b.getFinalTotal());
        cv.put(Constants.COL_STATUS,           b.getStatus());
        cv.put(Constants.COL_CURRENT_READING,  b.getCurrentReading());
        cv.put(Constants.COL_PREVIOUS_READING, b.getPreviousReading());
        cv.put(Constants.COL_CONSUMPTION,      b.getConsumption());
        cv.put(Constants.COL_BILLING_MODEL,    b.getBillingModel() != null
                ? b.getBillingModel() : Constants.BILLING_MODEL_FLAT);
        cv.put(Constants.COL_CREATED_AT,       DateUtils.now());
        cv.put(Constants.COL_UPDATED_AT,       DateUtils.now());
        return cv;
    }

    Bill fromCursor(Cursor c) {
        return new Bill(
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_ID)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_OWNER_UID)),
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_CUSTOMER_ID)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_MONTH)),
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_AMPS)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_PRICE_PER_AMP)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_TOTAL)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_PREVIOUS_BALANCE)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_FINAL_TOTAL)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_STATUS)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_CREATED_AT)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_UPDATED_AT)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_CURRENT_READING)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_PREVIOUS_READING)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_CONSUMPTION)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_BILLING_MODEL))
        );
    }
}
