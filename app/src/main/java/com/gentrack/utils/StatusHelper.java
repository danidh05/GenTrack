package com.gentrack.utils;

import android.content.Context;
import android.widget.TextView;

import com.gentrack.R;

public final class StatusHelper {

    private StatusHelper() {}

    public static void applyStatusChip(TextView chip, String status) {
        int bgRes;
        switch (status != null ? status : "") {
            case Constants.STATUS_ACTIVE:
                bgRes = R.drawable.bg_status_active;
                break;
            case Constants.STATUS_UNPAID:
                bgRes = R.drawable.bg_status_unpaid;
                break;
            case Constants.STATUS_DISCONNECTED:
                bgRes = R.drawable.bg_status_disconnected;
                break;
            case Constants.STATUS_PAID:
                bgRes = R.drawable.bg_status_paid;
                break;
            case Constants.STATUS_PARTIAL:
                bgRes = R.drawable.bg_status_partial;
                break;
            default:
                bgRes = R.drawable.bg_status_unpaid;
                break;
        }
        chip.setBackgroundResource(bgRes);
        chip.setText(status != null ? status : "");
    }

    public static int getStatusColor(Context context, String status) {
        int colorRes;
        switch (status != null ? status : "") {
            case Constants.STATUS_ACTIVE:
            case Constants.STATUS_PAID:
                colorRes = R.color.statusActive;
                break;
            case Constants.STATUS_UNPAID:
            case Constants.STATUS_PARTIAL:
                colorRes = R.color.statusUnpaid;
                break;
            case Constants.STATUS_DISCONNECTED:
                colorRes = R.color.statusDisconnected;
                break;
            default:
                colorRes = R.color.textHint;
                break;
        }
        return context.getResources().getColor(colorRes, context.getTheme());
    }
}
