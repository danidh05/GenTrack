package com.gentrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gentrack.R;
import com.gentrack.models.Announcement;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.ViewHolder> {

    private final List<Announcement> items;

    public AnnouncementAdapter(List<Announcement> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Announcement a = items.get(position);
        holder.tvTitle.setText(a.getTitle() != null ? a.getTitle() : "");
        holder.tvMessage.setText(a.getMessage() != null ? a.getMessage() : "");
        Date date = a.getCreatedAt() != null ? a.getCreatedAt().toDate() : null;
        holder.tvTimestamp.setText(date != null ? getRelativeTime(date) : "");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String getRelativeTime(Date date) {
        long diff    = System.currentTimeMillis() - date.getTime();
        long minutes = diff / 60_000;
        long hours   = minutes / 60;
        long days    = hours / 24;

        if (minutes < 1)  return "Just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " min ago"  : " mins ago");
        if (hours   < 24) return hours   + (hours   == 1 ? " hour ago" : " hours ago");
        if (days    == 1) return "Yesterday";
        if (days    < 7)  return days    + " days ago";
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMessage;
        final TextView tvTimestamp;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle     = itemView.findViewById(R.id.tvAnnouncementTitle);
            tvMessage   = itemView.findViewById(R.id.tvAnnouncementMessage);
            tvTimestamp = itemView.findViewById(R.id.tvAnnouncementTimestamp);
        }
    }
}
