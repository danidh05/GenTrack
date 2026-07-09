package com.gentrack.services;

import android.content.Context;

import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.models.Payment;
import com.gentrack.network.VolleyService;
import com.gentrack.utils.Constants;

import java.util.List;

public class PaymentService {

    private static PaymentService instance;
    private final DatabaseHandler db;
    private final Context         appContext;

    private PaymentService(Context context) {
        appContext = context.getApplicationContext();
        db         = DatabaseHandler.getInstance(appContext);
    }

    public static synchronized PaymentService getInstance(Context context) {
        if (instance == null) instance = new PaymentService(context);
        return instance;
    }

    /**
     * Records a payment: fetches the bill, sums existing payments, inserts the new payment,
     * updates bill status — all in one DB transaction. Then triggers customer status
     * recalculation and fires Volley writes (fire-and-forget).
     */
    public void recordPayment(int billId, double amountPaid, String ownerUid, String date,
                              DbCallback<Payment> cb) {
        db.recordPaymentTransaction(billId, amountPaid, ownerUid, date,
                new DbCallback<DatabaseHandler.PaymentTransactionResult>() {
                    @Override
                    public void onResult(DatabaseHandler.PaymentTransactionResult result) {
                        if (result == null) {
                            if (cb != null) cb.onResult(null);
                            return;
                        }
                        Payment payment     = result.payment;
                        Bill    updatedBill  = result.updatedBill;

                        // If this bill is now fully paid and carried a previous balance,
                        // close the prior bills whose debt was included in this payment.
                        if (Constants.STATUS_PAID.equals(updatedBill.getStatus())
                                && updatedBill.getPreviousBalance() > 0
                                && updatedBill.getMonth() != null) {
                            db.closePriorBills(updatedBill.getCustomerId(), ownerUid,
                                    updatedBill.getMonth(),
                                    new DbCallback<List<Bill>>() {
                                        @Override public void onResult(List<Bill> closedBills) {
                                            finishSync(payment, updatedBill, ownerUid,
                                                    closedBills, cb);
                                        }
                                        @Override public void onError(String e) {
                                            finishSync(payment, updatedBill, ownerUid, null, cb);
                                        }
                                    });
                        } else {
                            finishSync(payment, updatedBill, ownerUid, null, cb);
                        }
                    }
                    @Override
                    public void onError(String e) { if (cb != null) cb.onError(e); }
                });
    }

    private void finishSync(Payment payment, Bill updatedBill, String ownerUid,
                            List<Bill> closedBills, DbCallback<Payment> cb) {
        BillingService.getInstance(appContext).recalculateCustomerStatus(
                updatedBill.getCustomerId(), ownerUid, () -> {
                    VolleyService volley = VolleyService.getInstance(appContext);
                    volley.postPayment(payment, null);
                    volley.updateBill(updatedBill, null);
                    if (closedBills != null) {
                        for (Bill b : closedBills) volley.updateBill(b, null);
                    }
                    if (cb != null) cb.onResult(payment);
                });
    }

    /**
     * Returns the current remaining balance for a bill via callback.
     * Uses SQL SUM — does not iterate payments in Java.
     */
    public void getRemainingBalance(int billId, String ownerUid, DbCallback<Double> cb) {
        db.getBillById(billId, ownerUid, new DbCallback<Bill>() {
            @Override public void onResult(Bill bill) {
                if (bill == null) { if (cb != null) cb.onResult(0.0); return; }
                db.getSumPaymentsForBill(billId, ownerUid, new DbCallback<Double>() {
                    @Override public void onResult(Double paid) {
                        if (cb != null) cb.onResult(Math.max(0, bill.getFinalTotal() - paid));
                    }
                    @Override public void onError(String e) { if (cb != null) cb.onError(e); }
                });
            }
            @Override public void onError(String e) { if (cb != null) cb.onError(e); }
        });
    }
}
