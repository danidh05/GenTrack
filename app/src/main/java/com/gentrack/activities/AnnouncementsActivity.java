package com.gentrack.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gentrack.R;
import com.gentrack.adapters.AnnouncementAdapter;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Announcement;
import com.gentrack.services.AnnouncementService;
import com.gentrack.utils.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AnnouncementsActivity extends BaseActivity {

    private RecyclerView        rvAnnouncements;
    private View                layoutEmpty;
    private AnnouncementAdapter adapter;
    private final List<Announcement> announcements = new ArrayList<>();
    private String ownerUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcements);
        setupToolbarWithBack(getString(R.string.title_announcements));

        ownerUid = SessionManager.getInstance(this).getUid();

        rvAnnouncements = findViewById(R.id.rvAnnouncements);
        layoutEmpty     = findViewById(R.id.layoutEmpty);

        adapter = new AnnouncementAdapter(announcements);
        rvAnnouncements.setLayoutManager(new LinearLayoutManager(this));
        rvAnnouncements.setAdapter(adapter);

        FloatingActionButton fabCompose = findViewById(R.id.fabCompose);
        fabCompose.setOnClickListener(v -> showComposeDialog());

        loadAnnouncements();
    }

    private boolean isActive() {
        return !isFinishing() && !isDestroyed();
    }

    private void loadAnnouncements() {
        AnnouncementService.getInstance()
                .getAnnouncements(ownerUid, 10, new DbCallback<List<Announcement>>() {
                    @Override public void onResult(List<Announcement> result) {
                        if (!isActive()) return;
                        announcements.clear();
                        announcements.addAll(result);
                        adapter.notifyDataSetChanged();
                        boolean empty = announcements.isEmpty();
                        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        rvAnnouncements.setVisibility(empty ? View.GONE : View.VISIBLE);
                    }

                    @Override public void onError(String e) {
                        if (!isActive()) return;
                        announcements.clear();
                        adapter.notifyDataSetChanged();
                        layoutEmpty.setVisibility(View.VISIBLE);
                        rvAnnouncements.setVisibility(View.GONE);
                        new AlertDialog.Builder(AnnouncementsActivity.this)
                                .setTitle(R.string.title_error)
                                .setMessage(R.string.error_announcements)
                                .setPositiveButton(R.string.action_ok, null)
                                .show();
                    }
                });
    }

    private void showComposeDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_compose_announcement, null);
        TextInputEditText etTitle   = dialogView.findViewById(R.id.etAnnouncementTitle);
        TextInputEditText etMessage = dialogView.findViewById(R.id.etAnnouncementMessage);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.action_post, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title   = etTitle.getText()   != null
                            ? etTitle.getText().toString().trim()   : "";
                    String message = etMessage.getText() != null
                            ? etMessage.getText().toString().trim() : "";
                    if (title.isEmpty() || message.isEmpty()) {
                        Toast.makeText(this,
                                R.string.error_announcement_empty_fields,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    postAnnouncement(title, message, dialog);
                }));

        dialog.show();
    }

    private void postAnnouncement(String title, String message, AlertDialog dialog) {
        AnnouncementService.getInstance()
                .postAnnouncement(ownerUid, title, message, new DbCallback<Void>() {
                    @Override public void onResult(Void result) {
                        if (!isActive()) return;
                        dialog.dismiss();
                        Toast.makeText(AnnouncementsActivity.this,
                                R.string.msg_announcement_posted,
                                Toast.LENGTH_SHORT).show();
                        loadAnnouncements();
                    }

                    @Override public void onError(String e) {
                        if (!isActive()) return;

                        Toast.makeText(AnnouncementsActivity.this,
                                R.string.error_network, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
