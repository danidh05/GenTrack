package com.gentrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gentrack.R;
import com.gentrack.models.Customer;
import com.gentrack.utils.StatusHelper;

import java.util.ArrayList;
import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder>
        implements Filterable {

    public interface ActionListener {
        void onItemClick(Customer customer);
        void onEdit(Customer customer);
        void onDelete(Customer customer);
    }

    private List<Customer> fullList;
    private List<Customer> displayList;
    private final ActionListener listener;

    public CustomerAdapter(List<Customer> customers, ActionListener listener) {
        this.fullList    = new ArrayList<>(customers);
        this.displayList = new ArrayList<>(customers);
        this.listener    = listener;
    }

    public void updateList(List<Customer> newList) {
        this.fullList    = new ArrayList<>(newList);
        this.displayList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Customer c = displayList.get(position);

        h.tvName.setText(c.getName());
        h.tvPhone.setText(c.getPhone() != null && !c.getPhone().isEmpty()
                ? c.getPhone() : "No phone");
        h.tvLocation.setText(c.getLocation() != null && !c.getLocation().isEmpty()
                ? c.getLocation() : "No location");
        h.tvAmps.setText(c.getAmps() + " A subscription");

        Glide.with(h.itemView.getContext())
                .load(c.getImageUrl())
                .placeholder(R.drawable.bg_avatar_placeholder)
                .error(R.drawable.bg_avatar_placeholder)
                .circleCrop()
                .into(h.ivAvatar);

        StatusHelper.applyStatusChip(h.tvStatus, c.getStatus());

        int stripColor = StatusHelper.getStatusColor(h.itemView.getContext(), c.getStatus());
        h.viewStatusStrip.setBackgroundColor(stripColor);

        h.itemView.setOnClickListener(v -> listener.onItemClick(c));
        h.btnEdit.setOnClickListener(v -> listener.onEdit(c));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(c));
    }

    @Override
    public int getItemCount() { return displayList.size(); }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = new ArrayList<>(fullList);
                    results.count  = fullList.size();
                    return results;
                }
                String query = constraint.toString().toLowerCase().trim();
                List<Customer> filtered = new ArrayList<>();
                for (Customer c : fullList) {
                    boolean nameMatch = c.getName() != null
                            && c.getName().toLowerCase().contains(query);
                    boolean phoneMatch = c.getPhone() != null
                            && c.getPhone().toLowerCase().contains(query);
                    boolean locationMatch = c.getLocation() != null
                            && c.getLocation().toLowerCase().contains(query);
                    if (nameMatch || phoneMatch || locationMatch) filtered.add(c);
                }
                results.values = filtered;
                results.count  = filtered.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                displayList = (List<Customer>) results.values;
                notifyDataSetChanged();
            }
        };
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View        viewStatusStrip;
        final ImageView   ivAvatar;
        final TextView    tvName;
        final TextView    tvPhone;
        final TextView    tvLocation;
        final TextView    tvAmps;
        final TextView    tvStatus;
        final ImageButton btnEdit;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusStrip = itemView.findViewById(R.id.viewStatusStrip);
            ivAvatar        = itemView.findViewById(R.id.ivAvatar);
            tvName          = itemView.findViewById(R.id.tvName);
            tvPhone         = itemView.findViewById(R.id.tvPhone);
            tvLocation      = itemView.findViewById(R.id.tvLocation);
            tvAmps          = itemView.findViewById(R.id.tvAmps);
            tvStatus        = itemView.findViewById(R.id.tvStatus);
            btnEdit         = itemView.findViewById(R.id.btnEdit);
            btnDelete       = itemView.findViewById(R.id.btnDelete);
        }
    }
}
