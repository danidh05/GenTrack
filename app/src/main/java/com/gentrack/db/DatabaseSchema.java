package com.gentrack.db;

import com.gentrack.utils.Constants;

class DatabaseSchema {

    private DatabaseSchema() {}

    static final String CREATE_CUSTOMERS =
            "CREATE TABLE " + Constants.TABLE_CUSTOMERS + " ("
            + Constants.COL_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + Constants.COL_OWNER_UID  + " TEXT NOT NULL, "
            + Constants.COL_NAME       + " TEXT NOT NULL, "
            + Constants.COL_PHONE      + " TEXT, "
            + Constants.COL_LOCATION   + " TEXT, "
            + Constants.COL_AMPS       + " INTEGER NOT NULL, "
            + Constants.COL_STATUS     + " TEXT NOT NULL DEFAULT '" + Constants.STATUS_ACTIVE + "', "
            + Constants.COL_NOTES      + " TEXT, "
            + Constants.COL_IMAGE_URL  + " TEXT, "
            + Constants.COL_CREATED_AT + " TEXT NOT NULL, "
            + Constants.COL_UPDATED_AT + " TEXT NOT NULL"
            + ")";

    static final String CREATE_BILLS =
            "CREATE TABLE " + Constants.TABLE_BILLS + " ("
            + Constants.COL_ID               + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + Constants.COL_OWNER_UID        + " TEXT NOT NULL, "
            + Constants.COL_CUSTOMER_ID      + " INTEGER NOT NULL, "
            + Constants.COL_MONTH            + " TEXT NOT NULL, "
            + Constants.COL_AMPS             + " INTEGER NOT NULL, "
            + Constants.COL_PRICE_PER_AMP    + " REAL NOT NULL, "
            + Constants.COL_TOTAL            + " REAL NOT NULL, "
            + Constants.COL_PREVIOUS_BALANCE + " REAL NOT NULL DEFAULT 0, "
            + Constants.COL_FINAL_TOTAL      + " REAL NOT NULL, "
            + Constants.COL_STATUS           + " TEXT NOT NULL DEFAULT '" + Constants.STATUS_BILL_UNPAID + "', "
            + Constants.COL_CURRENT_READING  + " REAL NOT NULL DEFAULT 0, "
            + Constants.COL_PREVIOUS_READING + " REAL NOT NULL DEFAULT 0, "
            + Constants.COL_CONSUMPTION      + " REAL NOT NULL DEFAULT 0, "
            + Constants.COL_BILLING_MODEL    + " TEXT NOT NULL DEFAULT '" + Constants.BILLING_MODEL_FLAT + "', "
            + Constants.COL_CREATED_AT       + " TEXT NOT NULL, "
            + Constants.COL_UPDATED_AT       + " TEXT NOT NULL, "
            + "FOREIGN KEY (" + Constants.COL_CUSTOMER_ID + ") REFERENCES "
            + Constants.TABLE_CUSTOMERS + "(" + Constants.COL_ID + ")"
            + ")";

    static final String CREATE_PAYMENTS =
            "CREATE TABLE " + Constants.TABLE_PAYMENTS + " ("
            + Constants.COL_ID                + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + Constants.COL_OWNER_UID         + " TEXT NOT NULL, "
            + Constants.COL_BILL_ID           + " INTEGER NOT NULL, "
            + Constants.COL_AMOUNT_PAID       + " REAL NOT NULL, "
            + Constants.COL_DATE              + " TEXT NOT NULL, "
            + Constants.COL_REMAINING_BALANCE + " REAL NOT NULL, "
            + Constants.COL_CREATED_AT        + " TEXT NOT NULL, "
            + Constants.COL_UPDATED_AT        + " TEXT NOT NULL, "
            + "FOREIGN KEY (" + Constants.COL_BILL_ID + ") REFERENCES "
            + Constants.TABLE_BILLS + "(" + Constants.COL_ID + ")"
            + ")";

    static final String CREATE_SYNC_QUEUE =
            "CREATE TABLE " + Constants.TABLE_SYNC_QUEUE + " ("
            + Constants.COL_ID           + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + Constants.COL_TABLE_NAME   + " TEXT NOT NULL, "
            + Constants.COL_OPERATION    + " TEXT NOT NULL, "
            + Constants.COL_PAYLOAD_JSON + " TEXT NOT NULL, "
            + Constants.COL_CREATED_AT   + " TEXT NOT NULL"
            + ")";

    static final String CREATE_UNIQUE_BILL_MONTH_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS " + Constants.INDEX_BILLS_OWNER_CUSTOMER_MONTH
            + " ON " + Constants.TABLE_BILLS + " ("
            + Constants.COL_OWNER_UID + ", "
            + Constants.COL_CUSTOMER_ID + ", "
            + Constants.COL_MONTH
            + ")";
}
