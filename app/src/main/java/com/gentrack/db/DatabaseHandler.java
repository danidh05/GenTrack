package com.gentrack.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.MonthlyReport;
import com.gentrack.models.Payment;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseHandler extends SQLiteOpenHelper {

    private static DatabaseHandler instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final CustomerDao  customerDao  = new CustomerDao();
    private final BillDao      billDao      = new BillDao();
    private final PaymentDao   paymentDao   = new PaymentDao();
    private final SyncQueueDao syncQueueDao = new SyncQueueDao();

    private DatabaseHandler(Context context) {
        super(context.getApplicationContext(), Constants.DB_NAME, null, Constants.DB_VERSION);
    }

    public static synchronized DatabaseHandler getInstance(Context context) {
        if (instance == null) instance = new DatabaseHandler(context);
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DatabaseSchema.CREATE_CUSTOMERS);
        db.execSQL(DatabaseSchema.CREATE_BILLS);
        db.execSQL(DatabaseSchema.CREATE_PAYMENTS);
        db.execSQL(DatabaseSchema.CREATE_SYNC_QUEUE);
        db.execSQL(DatabaseSchema.CREATE_UNIQUE_BILL_MONTH_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + Constants.TABLE_BILLS + " ADD COLUMN "
                    + Constants.COL_CURRENT_READING + " REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + Constants.TABLE_BILLS + " ADD COLUMN "
                    + Constants.COL_PREVIOUS_READING + " REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + Constants.TABLE_BILLS + " ADD COLUMN "
                    + Constants.COL_CONSUMPTION + " REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + Constants.TABLE_BILLS + " ADD COLUMN "
                    + Constants.COL_BILLING_MODEL + " TEXT NOT NULL DEFAULT '"
                    + Constants.BILLING_MODEL_FLAT + "'");
        }
        if (oldVersion < 3) {
            tryCreateUniqueBillMonthIndex(db);
        }
    }

    private void tryCreateUniqueBillMonthIndex(SQLiteDatabase db) {
        try {
            db.execSQL(DatabaseSchema.CREATE_UNIQUE_BILL_MONTH_INDEX);
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            // Existing installs may already contain duplicate historical bills for the same
            // owner/customer/month. Keep those databases usable; new inserts still check first.
        }
    }

    // ───── CUSTOMERS ─────

    public void insertCustomer(Customer c, DbCallback<Long> cb) {
        executor.execute(() -> {
            try {
                long id = customerDao.insert(getWritableDatabase(), c);
                deliver(cb, id);
            } catch (Exception e) {
                deliverError(cb, "Failed to insert customer: " + e.getMessage());
            }
        });
    }

    public void updateCustomer(Customer c, DbCallback<Integer> cb) {
        executor.execute(() -> {
            try {
                int rows = customerDao.update(getWritableDatabase(), c);
                deliver(cb, rows);
            } catch (Exception e) {
                deliverError(cb, "Failed to update customer: " + e.getMessage());
            }
        });
    }

    public void updateCustomerStatus(int customerId, String status, String ownerUid,
                                     DbCallback<Void> cb) {
        executor.execute(() -> {
            try {
                customerDao.updateStatus(getWritableDatabase(), customerId, status, ownerUid);
                deliver(cb, null);
            } catch (Exception e) {
                deliverError(cb, "Failed to update customer status: " + e.getMessage());
            }
        });
    }

    public void deleteCustomer(int id, String ownerUid, DbCallback<Boolean> cb) {
        executor.execute(() -> {
            try {
                boolean hasB = billDao.hasBillsForCustomer(getReadableDatabase(), id, ownerUid);
                if (hasB) { deliver(cb, false); return; }
                customerDao.delete(getWritableDatabase(), id, ownerUid);
                deliver(cb, true);
            } catch (Exception e) {
                deliverError(cb, "Failed to delete customer: " + e.getMessage());
            }
        });
    }

    public void getAllCustomers(String ownerUid, DbCallback<List<Customer>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, customerDao.getAll(getReadableDatabase(), ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get customers: " + e.getMessage());
            }
        });
    }

    public void getCustomerById(int id, String ownerUid, DbCallback<Customer> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, customerDao.getById(getReadableDatabase(), id, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get customer: " + e.getMessage());
            }
        });
    }

    public void getCustomersByStatus(String status, String ownerUid, DbCallback<List<Customer>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, customerDao.getByStatus(getReadableDatabase(), status, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get customers by status: " + e.getMessage());
            }
        });
    }

    public void getCustomerCountByStatus(String status, String ownerUid, DbCallback<Integer> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, customerDao.countByStatus(getReadableDatabase(), status, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to count customers by status: " + e.getMessage());
            }
        });
    }

    public void getCustomerStatusCounts(String ownerUid, DbCallback<Map<String, Integer>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, customerDao.getStatusCounts(getReadableDatabase(), ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get customer status counts: " + e.getMessage());
            }
        });
    }

    // ───── BILLS ─────

    public void insertBill(Bill b, DbCallback<Long> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.insert(getWritableDatabase(), b));
            } catch (Exception e) {
                deliverError(cb, "Failed to insert bill: " + e.getMessage());
            }
        });
    }

    public void getBillByCustomerAndMonth(int customerId, String ownerUid, String month,
                                          DbCallback<Bill> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getByCustomerAndMonth(getReadableDatabase(), customerId, ownerUid, month));
            } catch (Exception e) {
                deliverError(cb, "Failed to get bill by month: " + e.getMessage());
            }
        });
    }

    public void updateBill(Bill b, DbCallback<Integer> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.update(getWritableDatabase(), b));
            } catch (Exception e) {
                deliverError(cb, "Failed to update bill: " + e.getMessage());
            }
        });
    }

    public void getBillById(int id, String ownerUid, DbCallback<Bill> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getById(getReadableDatabase(), id, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get bill: " + e.getMessage());
            }
        });
    }

    public void getAllBillsForCustomer(int customerId, String ownerUid, DbCallback<List<Bill>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getAllForCustomer(getReadableDatabase(), customerId, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get bills for customer: " + e.getMessage());
            }
        });
    }

    public void getAllBillsForCustomerWithPaidAmounts(int customerId, String ownerUid,
                                                      DbCallback<List<Bill>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getAllForCustomerWithPaidAmounts(getReadableDatabase(), customerId, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get bills with paid amounts: " + e.getMessage());
            }
        });
    }

    public void getBillStatusesForCustomer(int customerId, String ownerUid,
                                           DbCallback<List<String>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getStatusesForCustomer(getReadableDatabase(), customerId, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get bill statuses: " + e.getMessage());
            }
        });
    }

    public void getPreviousBalance(int customerId, String ownerUid, String beforeMonth,
                                   DbCallback<Double> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getPreviousBalance(getReadableDatabase(), customerId, ownerUid, beforeMonth));
            } catch (Exception e) {
                deliverError(cb, "Failed to get previous balance: " + e.getMessage());
            }
        });
    }

    public void closePriorBills(int customerId, String ownerUid, String beforeMonth,
                                DbCallback<List<Bill>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.closePriorBills(getWritableDatabase(), customerId, ownerUid, beforeMonth));
            } catch (Exception e) {
                deliverError(cb, "Failed to close prior bills: " + e.getMessage());
            }
        });
    }

    public void hasBillsForCustomer(int customerId, String ownerUid, DbCallback<Boolean> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.hasBillsForCustomer(getReadableDatabase(), customerId, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to check bills for customer: " + e.getMessage());
            }
        });
    }

    public void getTotalOutstanding(String ownerUid, DbCallback<Double> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getTotalOutstanding(getReadableDatabase(), ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get total outstanding: " + e.getMessage());
            }
        });
    }

    public void getMonthlyRevenueReports(String ownerUid, int limit,
                                         DbCallback<List<MonthlyReport>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getMonthlyRevenueReports(getReadableDatabase(), ownerUid, limit));
            } catch (Exception e) {
                deliverError(cb, "Failed to get monthly revenue reports: " + e.getMessage());
            }
        });
    }

    public void getUnpaidBillsOlderThan(int days, String ownerUid, DbCallback<List<Bill>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getUnpaidOlderThan(getReadableDatabase(), days, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get unpaid bills: " + e.getMessage());
            }
        });
    }

    public void getSumPaymentsForBill(int billId, String ownerUid, DbCallback<Double> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, billDao.getSumPayments(getReadableDatabase(), billId, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get payment sum for bill: " + e.getMessage());
            }
        });
    }

    /**
     * Generates and inserts flat-rate bills for all provided customers in a single transaction.
     * Tier price is selected per customer based on their amp level.
     */
    public void insertBatchBillsForCustomers(List<Customer> customers, String month,
                                              double price5a, double price10a, double price15a,
                                              DbCallback<List<Bill>> cb) {
        executor.execute(() -> {
            try {
                SQLiteDatabase rdb = getReadableDatabase();
                SQLiteDatabase wdb = getWritableDatabase();
                List<Bill> bills = new ArrayList<>();
                for (Customer customer : customers) {
                    double prevBalance = billDao.getPreviousBalance(rdb, customer.getId(),
                            customer.getOwnerUid(), month);
                    double tierPrice;
                    if (customer.getAmps() <= 5)       tierPrice = price5a;
                    else if (customer.getAmps() <= 10) tierPrice = price10a;
                    else                               tierPrice = price15a;
                    double finalTotal = tierPrice + prevBalance;

                    Bill b = new Bill();
                    b.setOwnerUid(customer.getOwnerUid());
                    b.setCustomerId(customer.getId());
                    b.setMonth(month);
                    b.setAmps(customer.getAmps());
                    b.setPricePerAmp(tierPrice);
                    b.setTotal(tierPrice);
                    b.setPreviousBalance(prevBalance);
                    b.setFinalTotal(finalTotal);
                    b.setStatus(Constants.STATUS_BILL_UNPAID);
                    b.setCurrentReading(0);
                    b.setPreviousReading(0);
                    b.setConsumption(0);
                    b.setBillingModel(Constants.BILLING_MODEL_FLAT);
                    b.setCreatedAt(DateUtils.now());
                    b.setUpdatedAt(DateUtils.now());
                    bills.add(b);
                }
                List<Bill> inserted = billDao.insertBatch(wdb, bills);
                deliver(cb, inserted);
            } catch (Exception e) {
                deliverError(cb, "Failed to insert batch bills: " + e.getMessage());
            }
        });
    }

    /**
     * Atomically: fetches bill, sums existing payments, inserts the new payment, updates bill
     * status. Returns a {@link PaymentTransactionResult} containing the inserted Payment and the
     * updated Bill (with correct status). Returns null result if the bill is not found.
     */
    public void recordPaymentTransaction(int billId, double amountPaid, String ownerUid,
                                          String date, DbCallback<PaymentTransactionResult> cb) {
        executor.execute(() -> {
            try {
                SQLiteDatabase rdb = getReadableDatabase();
                SQLiteDatabase wdb = getWritableDatabase();

                Bill bill = billDao.getById(rdb, billId, ownerUid);
                if (bill == null) { deliver(cb, null); return; }

                double alreadyPaid  = billDao.getSumPayments(rdb, billId, ownerUid);
                double newRemaining = Math.max(0, bill.getFinalTotal() - alreadyPaid - amountPaid);
                String newStatus    = newRemaining <= 0 ? Constants.STATUS_PAID : Constants.STATUS_PARTIAL;

                Payment payment = new Payment();
                payment.setOwnerUid(ownerUid);
                payment.setBillId(billId);
                payment.setAmountPaid(amountPaid);
                payment.setDate(date);
                payment.setRemainingBalance(newRemaining);
                payment.setCreatedAt(DateUtils.now());
                payment.setUpdatedAt(DateUtils.now());

                long paymentId = paymentDao.insert(wdb, payment);
                payment.setId((int) paymentId);

                bill.setStatus(newStatus);
                billDao.update(wdb, bill);

                deliver(cb, new PaymentTransactionResult(payment, bill));
            } catch (Exception e) {
                deliverError(cb, "Failed to record payment: " + e.getMessage());
            }
        });
    }

    /**
     * Reads the customer's bill statuses, derives the correct customer status, writes it, and
     * returns the updated Customer object. Returns null if the customer is Disconnected or not
     * found (status is intentionally left unchanged in both cases).
     */
    public void computeAndUpdateCustomerStatus(int customerId, String ownerUid,
                                                DbCallback<Customer> cb) {
        executor.execute(() -> {
            try {
                SQLiteDatabase rdb = getReadableDatabase();
                Customer customer = customerDao.getById(rdb, customerId, ownerUid);
                if (customer == null || Constants.STATUS_DISCONNECTED.equals(customer.getStatus())) {
                    deliver(cb, null);
                    return;
                }

                List<String> statuses = billDao.getStatusesForCustomer(rdb, customerId, ownerUid);
                String newStatus;
                if (statuses.isEmpty()) {
                    newStatus = Constants.STATUS_ACTIVE;
                } else {
                    boolean hasOpen = false;
                    for (String s : statuses) {
                        if (Constants.STATUS_BILL_UNPAID.equals(s) || Constants.STATUS_PARTIAL.equals(s)) {
                            hasOpen = true;
                            break;
                        }
                    }
                    newStatus = hasOpen ? Constants.STATUS_UNPAID : Constants.STATUS_ACTIVE;
                }

                customerDao.updateStatus(getWritableDatabase(), customerId, newStatus, ownerUid);
                customer.setStatus(newStatus);
                deliver(cb, customer);
            } catch (Exception e) {
                deliverError(cb, "Failed to compute customer status: " + e.getMessage());
            }
        });
    }

    // ───── PAYMENTS ─────

    public void insertPayment(Payment p, DbCallback<Long> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, paymentDao.insert(getWritableDatabase(), p));
            } catch (Exception e) {
                deliverError(cb, "Failed to insert payment: " + e.getMessage());
            }
        });
    }

    public void getAllPaymentsForBill(int billId, String ownerUid, DbCallback<List<Payment>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, paymentDao.getAllForBill(getReadableDatabase(), billId, ownerUid));
            } catch (Exception e) {
                deliverError(cb, "Failed to get payments for bill: " + e.getMessage());
            }
        });
    }

    // ───── SYNC QUEUE ─────

    public void insertSyncQueueItem(String tableName, String operation, String payloadJson,
                                    DbCallback<Void> cb) {
        executor.execute(() -> {
            try {
                syncQueueDao.insert(getWritableDatabase(), tableName, operation, payloadJson);
                deliver(cb, null);
            } catch (Exception e) {
                deliverError(cb, "Failed to insert sync queue item: " + e.getMessage());
            }
        });
    }

    public void getAllSyncQueueItems(DbCallback<List<SyncQueueItem>> cb) {
        executor.execute(() -> {
            try {
                deliver(cb, syncQueueDao.getAll(getReadableDatabase()));
            } catch (Exception e) {
                deliverError(cb, "Failed to get sync queue items: " + e.getMessage());
            }
        });
    }

    public void deleteSyncQueueItem(int id, DbCallback<Void> cb) {
        executor.execute(() -> {
            try {
                syncQueueDao.delete(getWritableDatabase(), id);
                deliver(cb, null);
            } catch (Exception e) {
                deliverError(cb, "Failed to delete sync queue item: " + e.getMessage());
            }
        });
    }

    public void deleteMatchingSyncQueueItems(String tableName, String operation, String payloadJson,
                                             DbCallback<Void> cb) {
        executor.execute(() -> {
            try {
                syncQueueDao.deleteMatching(getWritableDatabase(), tableName, operation, payloadJson);
                deliver(cb, null);
            } catch (Exception e) {
                deliverError(cb, "Failed to delete matching sync queue items: " + e.getMessage());
            }
        });
    }

    // ───── HELPERS ─────

    private <T> void deliver(DbCallback<T> cb, T result) {
        if (cb != null) mainHandler.post(() -> cb.onResult(result));
    }

    private <T> void deliverError(DbCallback<T> cb, String error) {
        if (cb != null) mainHandler.post(() -> cb.onError(error));
    }

    // ───── INNER CLASSES ─────

    public static class SyncQueueItem {
        public final int    id;
        public final String tableName;
        public final String operation;
        public final String payloadJson;

        public SyncQueueItem(int id, String tableName, String operation, String payloadJson) {
            this.id          = id;
            this.tableName   = tableName;
            this.operation   = operation;
            this.payloadJson = payloadJson;
        }
    }

    public static class PaymentTransactionResult {
        public final Payment payment;
        public final Bill    updatedBill;

        public PaymentTransactionResult(Payment payment, Bill updatedBill) {
            this.payment     = payment;
            this.updatedBill = updatedBill;
        }
    }
}
