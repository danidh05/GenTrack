package com.gentrack;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.gentrack.utils.Constants;
import com.gentrack.utils.NotificationHelper;
import com.gentrack.workers.UnpaidBillWorker;

import java.util.concurrent.TimeUnit;

public class GenTrackApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        scheduleUnpaidBillReminder();
    }

    private void scheduleUnpaidBillReminder() {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                UnpaidBillWorker.class, 1, TimeUnit.DAYS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                Constants.WORK_UNPAID_REMINDER,
                ExistingPeriodicWorkPolicy.KEEP,
                work);
    }
}
