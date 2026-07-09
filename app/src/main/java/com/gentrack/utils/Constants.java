package com.gentrack.utils;

public final class Constants {

    private Constants() {}

    // API
    public static final String API_BASE_URL = "http://gentrack.atwebpages.com";
    public static final String API_CUSTOMERS_CREATE  = API_BASE_URL + "/customers/create.php";
    public static final String API_CUSTOMERS_UPDATE  = API_BASE_URL + "/customers/update.php";
    public static final String API_CUSTOMERS_DELETE  = API_BASE_URL + "/customers/delete.php";
    public static final String API_BILLS_CREATE      = API_BASE_URL + "/bills/create.php";
    public static final String API_BILLS_UPDATE      = API_BASE_URL + "/bills/update.php";
    public static final String API_PAYMENTS_CREATE   = API_BASE_URL + "/payments/create.php";
    public static final String API_CONFIG_RATES      = API_BASE_URL + "/config/get.php";
    public static final String API_REPORTS_SAVE      = API_BASE_URL + "/reports/save.php";
    public static final String API_REPORTS_MONTHLY   = API_BASE_URL + "/reports/list.php";
    public static final String API_CONFIG_UPDATE     = API_BASE_URL + "/config/update.php";

    // Database
    public static final String DB_NAME    = "gentrack.db";
    public static final int    DB_VERSION = 3;

    // Tables
    public static final String TABLE_CUSTOMERS  = "customers";
    public static final String TABLE_BILLS      = "bills";
    public static final String TABLE_PAYMENTS   = "payments";
    public static final String TABLE_SYNC_QUEUE = "sync_queue";

    // Common columns
    public static final String COL_ID         = "id";
    public static final String COL_OWNER_UID  = "owner_uid";
    public static final String COL_STATUS     = "status";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    // Customer-specific columns
    public static final String COL_NAME      = "name";
    public static final String COL_PHONE     = "phone";
    public static final String COL_LOCATION  = "location";
    public static final String COL_AMPS      = "amps";
    public static final String COL_NOTES     = "notes";
    public static final String COL_IMAGE_URL = "image_url";

    // Bill-specific columns
    public static final String COL_CUSTOMER_ID      = "customer_id";
    public static final String COL_MONTH            = "month";
    public static final String COL_PRICE_PER_AMP    = "price_per_amp";
    public static final String COL_TOTAL            = "total";
    public static final String COL_PREVIOUS_BALANCE = "previous_balance";
    public static final String COL_FINAL_TOTAL      = "final_total";
    public static final String COL_CURRENT_READING  = "current_reading";
    public static final String COL_PREVIOUS_READING = "previous_reading";
    public static final String COL_CONSUMPTION      = "consumption";
    public static final String COL_BILLING_MODEL    = "billing_model";
    public static final String INDEX_BILLS_OWNER_CUSTOMER_MONTH =
            "idx_bills_owner_customer_month";

    // Billing model values
    public static final String BILLING_MODEL_FLAT             = "flat";
    public static final String BILLING_MODEL_CONSUMPTION      = "consumption";       // legacy
    public static final String BILLING_MODEL_BASE_CONSUMPTION = "base_consumption";  // base fee + kWh

    // Payment-specific columns
    public static final String COL_BILL_ID           = "bill_id";
    public static final String COL_AMOUNT_PAID       = "amount_paid";
    public static final String COL_DATE              = "date";
    public static final String COL_REMAINING_BALANCE = "remaining_balance";

    // Sync-queue columns
    public static final String COL_TABLE_NAME   = "table_name";
    public static final String COL_OPERATION    = "operation";
    public static final String COL_PAYLOAD_JSON = "payload_json";

    // Customer status values
    public static final String STATUS_ACTIVE       = "Active";
    public static final String STATUS_UNPAID       = "Unpaid";
    public static final String STATUS_DISCONNECTED = "Disconnected";

    // Bill status values
    public static final String STATUS_PAID       = "Paid";
    public static final String STATUS_PARTIAL    = "Partial";
    public static final String STATUS_BILL_UNPAID = STATUS_UNPAID; // alias — use at bill call sites

    // Default phone country code (change here if business is not in North America)
    public static final String DEFAULT_COUNTRY_CODE = "+1";

    // SharedPreferences
    public static final String PREF_NAME                 = "GenTrackPrefs";
    public static final String PREF_UID                  = "uid";
    public static final String PREF_EMAIL                = "email";
    public static final String PREF_DEFAULT_PRICE_PER_AMP = "default_price_per_amp";
    public static final String PREF_PRICE_5A             = "price_5a";
    public static final String PREF_PRICE_10A            = "price_10a";
    public static final String PREF_PRICE_15A            = "price_15a";
    public static final String PREF_PRICE_PER_KWH        = "price_per_kwh";
    public static final String PREF_BASE_PRICE_5A        = "base_price_5a";
    public static final String PREF_BASE_PRICE_10A       = "base_price_10a";
    public static final String PREF_BASE_PRICE_15A       = "base_price_15a";
    public static final String PREF_CURRENCY             = "currency";

    // WorkManager
    public static final String WORK_UNPAID_REMINDER = "unpaid_reminder";

    // Notification
    public static final String NOTIFICATION_CHANNEL_ID   = "gentrack_reminders";
    public static final String NOTIFICATION_CHANNEL_NAME = "Payment Reminders";

    // FileProvider
    public static final String FILE_PROVIDER_AUTHORITY = "com.gentrack.fileprovider";

    // Firestore collections
    public static final String FIRESTORE_ANNOUNCEMENTS = "announcements";

    // Firebase Storage
    public static final String STORAGE_CUSTOMERS_PATH  = "customers";
    public static final String STORAGE_IMAGE_EXTENSION = ".jpg";

    // API param keys
    public static final String PARAM_OWNER_UID = "owner_uid";
    public static final String PARAM_UID       = "uid";
    public static final String PARAM_LIMIT     = "limit";
}
