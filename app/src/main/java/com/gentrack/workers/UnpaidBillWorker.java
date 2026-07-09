package com.gentrack.workers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.utils.NotificationHelper;
import com.gentrack.utils.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class UnpaidBillWorker extends Worker {

    public UnpaidBillWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String ownerUid = SessionManager.getInstance(context).getUid();
        if (ownerUid.isEmpty()) return Result.success();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }

        // DatabaseHandler dispatches async and delivers on main thread; use latch to
        // block the WorkManager background thread until each result arrives.
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Bill>> billsRef = new AtomicReference<>(new ArrayList<>());

        DatabaseHandler db = DatabaseHandler.getInstance(context);
        db.getUnpaidBillsOlderThan(30, ownerUid, new DbCallback<List<Bill>>() {
            @Override public void onResult(List<Bill> bills) {
                if (bills != null) billsRef.set(bills);
                latch.countDown();
            }
            @Override public void onError(String e) { latch.countDown(); }
        });

        try { latch.await(); } catch (InterruptedException e) { return Result.retry(); }

        Map<Integer, List<Bill>> grouped = new HashMap<>();
        for (Bill bill : billsRef.get()) {
            grouped.computeIfAbsent(bill.getCustomerId(), k -> new ArrayList<>()).add(bill);
        }

        for (Map.Entry<Integer, List<Bill>> entry : grouped.entrySet()) {
            CountDownLatch customerLatch = new CountDownLatch(1);
            AtomicReference<Customer> customerRef = new AtomicReference<>();

            db.getCustomerById(entry.getKey(), ownerUid, new DbCallback<Customer>() {
                @Override public void onResult(Customer customer) {
                    customerRef.set(customer);
                    customerLatch.countDown();
                }
                @Override public void onError(String e) { customerLatch.countDown(); }
            });

            try { customerLatch.await(); } catch (InterruptedException e) { continue; }

            Customer customer = customerRef.get();
            if (customer == null) continue;

            // Use customer.getId() so repeated firings replace the same notification
            NotificationHelper.showOverdueNotification(context, customer.getId(),
                    customer.getName(), entry.getValue().size());
        }

        return Result.success();
    }
}
