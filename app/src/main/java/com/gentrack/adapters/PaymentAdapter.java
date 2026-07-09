package com.gentrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gentrack.R;
import com.gentrack.models.Payment;

import java.util.ArrayList;
import java.util.List;

public class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.ViewHolder> {

    private List<Payment> payments;

    public PaymentAdapter(List<Payment> payments) {
        this.payments = new ArrayList<>(payments);
    }

    public void updateList(List<Payment> newList) {
        this.payments = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Payment payment = payments.get(position);

        h.tvPaymentDate.setText(payment.getDate());
        h.tvAmountPaid.setText(String.format("$%.2f", payment.getAmountPaid()));
        h.tvRemainingAfter.setText(
                String.format("$%.2f remaining", payment.getRemainingBalance()));
    }

    @Override
    public int getItemCount() { return payments.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPaymentDate;
        final TextView tvAmountPaid;
        final TextView tvRemainingAfter;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPaymentDate    = itemView.findViewById(R.id.tvPaymentDate);
            tvAmountPaid     = itemView.findViewById(R.id.tvAmountPaid);
            tvRemainingAfter = itemView.findViewById(R.id.tvRemainingAfter);
        }
    }
}
