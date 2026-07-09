package com.gentrack.services;

import android.content.Context;

import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.network.VolleyService;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;
import com.gentrack.utils.SessionManager;

import java.util.List;

public class BillingService {

    private static BillingService instance;
    private final DatabaseHandler db;
    private final Context         appContext;
    private final SessionManager  session;

    private BillingService(Context context) {
        appContext = context.getApplicationContext();
        db         = DatabaseHandler.getInstance(appContext);
        session    = SessionManager.getInstance(appContext);
    }

    public static synchronized BillingService getInstance(Context context) {
        if (instance == null) instance = new BillingService(context);
        return instance;
    }

    private double getTierPrice(int amps) {
        if (amps <= 5)  return session.getPrice5a();
        if (amps <= 10) return session.getPrice10a();
        return session.getPrice15a();
    }

    private double getBaseTierPrice(int amps) {
        if (amps <= 5)  return session.getBasePrice5a();
        if (amps <= 10) return session.getBasePrice10a();
        return session.getBasePrice15a();
    }

    /**
     * Calculates a new Bill (does NOT insert). Async because it reads previousBalance from DB.
     *
     * billingModel must be BILLING_MODEL_FLAT or BILLING_MODEL_BASE_CONSUMPTION.
     *   FLAT: total = tier flat price (price5a/10a/15a). Readings ignored.
     *   BASE_CONSUMPTION: total = baseTierFee + (consumption × pricePerKwh).
     *     pricePerAmp stores kWhRate; baseFee is recoverable as total − consumption×pricePerAmp.
     */
    public void generateBill(Customer customer, String month, String billingModel,
                             double currentReading, double previousReading,
                             DbCallback<Bill> cb) {
        db.getPreviousBalance(customer.getId(), customer.getOwnerUid(), month, new DbCallback<Double>() {
            @Override public void onResult(Double prevBalance) {
                double prev = (prevBalance != null) ? prevBalance : 0.0;

                double priceUnit;
                double total;
                double consumption = 0;
                double curR = 0;
                double prevR = 0;
                String model;

                if (Constants.BILLING_MODEL_BASE_CONSUMPTION.equals(billingModel)) {
                    double baseFee = getBaseTierPrice(customer.getAmps());
                    double kwhRate = session.getPricePerKwh();
                    if (baseFee <= 0 || kwhRate <= 0) {
                        if (cb != null) cb.onError("Pricing not configured. Please set prices in Pricing Config.");
                        return;
                    }
                    model       = Constants.BILLING_MODEL_BASE_CONSUMPTION;
                    consumption = currentReading - previousReading;
                    curR        = currentReading;
                    prevR       = previousReading;
                    priceUnit   = kwhRate;
                    total       = baseFee + consumption * kwhRate;
                } else {
                    double tierPrice = getTierPrice(customer.getAmps());
                    if (tierPrice <= 0) {
                        if (cb != null) cb.onError("Pricing not configured. Please set prices in Pricing Config.");
                        return;
                    }
                    model     = Constants.BILLING_MODEL_FLAT;
                    priceUnit = tierPrice;
                    total     = priceUnit;
                }

                double finalTotal = total + prev;

                Bill bill = new Bill();
                bill.setOwnerUid(customer.getOwnerUid());
                bill.setCustomerId(customer.getId());
                bill.setMonth(month);
                bill.setAmps(customer.getAmps());
                bill.setPricePerAmp(priceUnit);
                bill.setTotal(total);
                bill.setPreviousBalance(prev);
                bill.setFinalTotal(finalTotal);
                bill.setStatus(Constants.STATUS_BILL_UNPAID);
                bill.setCurrentReading(curR);
                bill.setPreviousReading(prevR);
                bill.setConsumption(consumption);
                bill.setBillingModel(model);
                bill.setCreatedAt(DateUtils.now());
                bill.setUpdatedAt(DateUtils.now());
                if (cb != null) cb.onResult(bill);
            }
            @Override public void onError(String e) { if (cb != null) cb.onError(e); }
        });
    }

    /**
     * Reads bill statuses, derives customer status, writes it, fires Volley (fire-and-forget).
     * Disconnected customers are never touched. onComplete runs on main thread when done.
     */
    public void recalculateCustomerStatus(int customerId, String ownerUid, Runnable onComplete) {
        db.computeAndUpdateCustomerStatus(customerId, ownerUid, new DbCallback<Customer>() {
            @Override public void onResult(Customer updated) {
                if (updated != null) {
                    VolleyService.getInstance(appContext).updateCustomer(updated, null);
                }
                if (onComplete != null) onComplete.run();
            }
            @Override public void onError(String e) {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    /**
     * Generates and inserts flat-rate bills for all active customers using tier pricing from
     * remote config. Fires Volley writes and status recalculation after the transaction.
     */
    public void generateBatchBills(List<Customer> activeCustomers, String month,
                                   BatchCallback callback) {
        double price5a  = session.getPrice5a();
        double price10a = session.getPrice10a();
        double price15a = session.getPrice15a();
        if (price5a <= 0 || price10a <= 0 || price15a <= 0) {
            if (callback != null) callback.onComplete(0,0);
            return;
        }
        db.insertBatchBillsForCustomers(activeCustomers, month, price5a, price10a, price15a,
                new DbCallback<List<Bill>>() {
                    @Override public void onResult(List<Bill> inserted) {
                        VolleyService volley = VolleyService.getInstance(appContext);
                        double totalRevenue = 0;
                        for (Bill bill : inserted) {
                            totalRevenue += bill.getFinalTotal();
                            recalculateCustomerStatus(bill.getCustomerId(),
                                    bill.getOwnerUid(), null);
                            volley.postBill(bill, null);
                        }
                        if (callback != null) callback.onComplete(inserted.size(), totalRevenue);
                    }
                    @Override public void onError(String e) {
                        if (callback != null) callback.onComplete(0, 0);
                    }
                });
    }

    public interface BatchCallback {
        void onComplete(int count, double totalExpectedRevenue);
    }
}
