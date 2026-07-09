package com.gentrack.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.gentrack.models.Payment;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;

class PaymentDao {

    long insert(SQLiteDatabase db, Payment p) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COL_OWNER_UID,p.getOwnerUid());
        cv.put(Constants.COL_BILL_ID,p.getBillId());
        cv.put(Constants.COL_AMOUNT_PAID,p.getAmountPaid());
        cv.put(Constants.COL_DATE,p.getDate());
        cv.put(Constants.COL_REMAINING_BALANCE, p.getRemainingBalance());
        cv.put(Constants.COL_CREATED_AT,DateUtils.now());
        cv.put(Constants.COL_UPDATED_AT,DateUtils.now());
        return db.insert(Constants.TABLE_PAYMENTS, null, cv);
    }

    List<Payment> getAllForBill(SQLiteDatabase db, int billId, String ownerUid) {
        List<Payment> list = new ArrayList<>();
        Cursor c = db.query(Constants.TABLE_PAYMENTS, null,
                Constants.COL_BILL_ID + "=? AND " + Constants.COL_OWNER_UID + "=?",
                new String[]{String.valueOf(billId), ownerUid},
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

    private Payment fromCursor(Cursor c) {
        return new Payment(
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_ID)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_OWNER_UID)),
                c.getInt(c.getColumnIndexOrThrow(Constants.COL_BILL_ID)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_AMOUNT_PAID)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_DATE)),
                c.getDouble(c.getColumnIndexOrThrow(Constants.COL_REMAINING_BALANCE)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_CREATED_AT)),
                c.getString(c.getColumnIndexOrThrow(Constants.COL_UPDATED_AT))
        );
    }
}
