package com.gentrack.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.gentrack.R;

public final class NotificationHelper {

    private NotificationHelper() {}

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Reminders for overdue unpaid bills");
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    public static void showOverdueNotification(Context context, int notifId,
                                               String customerName, int overdueCount) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String message = customerName + " has " + overdueCount
                + " overdue unpaid bill" + (overdueCount == 1 ? "" : "s");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("Overdue Bills")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(notifId, builder.build());
    }
}
