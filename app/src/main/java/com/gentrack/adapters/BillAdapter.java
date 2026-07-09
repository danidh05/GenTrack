package com.gentrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gentrack.R;
import com.gentrack.models.Bill;
import com.gentrack.utils.Constants;
import com.gentrack.utils.StatusHelper;

import java.util.ArrayList;
import java.util.List;

public class BillAdapter extends RecyclerView.Adapter<BillAdapter.ViewHolder> {

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
    }

    private List<Bill> bills;
    private final OnBillClickListener listener;

    public BillAdapter(List<Bill> bills, OnBillClickListener listener) {
        this.bills    = new ArrayList<>(bills);
        this.listener = listener;
    }

    public void updateList(List<Bill> newList) {
        this.bills = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bill, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Bill bill = bills.get(position);

        h.tvMonth.setText(bill.getMonth());
        h.tvFinalTotal.setText(String.format("$%.2f", bill.getFinalTotal()));

        StatusHelper.applyStatusChip(h.tvStatus, bill.getStatus());

        int stripColor = StatusHelper.getStatusColor(h.itemView.getContext(), bill.getStatus());
        h.viewStatusStrip.setBackgroundColor(stripColor);

        int progress;
        String summary;
        double finalTotal  = bill.getFinalTotal();
        double totalPaid   = bill.getTransientTotalPaid();
        double remaining   = Math.max(0, finalTotal - totalPaid);
        if (Constants.STATUS_PAID.equals(bill.getStatus())) {
            progress = 100;
            summary  = h.itemView.getContext().getString(R.string.label_fully_paid);
        } else if (Constants.STATUS_PARTIAL.equals(bill.getStatus()) && finalTotal > 0) {
            progress = Math.min(99, (int) Math.round((totalPaid / finalTotal) * 100));
            summary  = h.itemView.getContext().getString(
                    R.string.label_due, String.format("$%.2f", remaining));
        } else {
            progress = 0;
            summary  = h.itemView.getContext().getString(
                    R.string.label_due, String.format("$%.2f", finalTotal));
        }
        h.pbPayment.setProgress(progress);
        h.tvPaymentSummary.setText(summary);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onBillClick(bill); });
    }

    @Override
    public int getItemCount() { return bills.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View        viewStatusStrip;
        final TextView    tvMonth;
        final TextView    tvStatus;
        final TextView    tvFinalTotal;
        final ProgressBar pbPayment;
        final TextView    tvPaymentSummary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusStrip  = itemView.findViewById(R.id.viewStatusStrip);
            tvMonth          = itemView.findViewById(R.id.tvMonth);
            tvStatus         = itemView.findViewById(R.id.tvStatus);
            tvFinalTotal     = itemView.findViewById(R.id.tvFinalTotal);
            pbPayment        = itemView.findViewById(R.id.pbPayment);
            tvPaymentSummary = itemView.findViewById(R.id.tvPaymentSummary);
        }
    }
}
