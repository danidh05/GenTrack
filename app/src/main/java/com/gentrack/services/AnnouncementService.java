package com.gentrack.services;

import android.util.Log;

import com.gentrack.db.DbCallback;
import com.gentrack.models.Announcement;
import com.gentrack.utils.Constants;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnouncementService {

    private static AnnouncementService instance;

    private AnnouncementService() {}

    public static synchronized AnnouncementService getInstance() {
        if (instance == null) instance = new AnnouncementService();
        return instance;
    }

    public void getLatest(String ownerUid, DbCallback<Announcement> cb) {
        getAnnouncements(ownerUid, 1, new DbCallback<List<Announcement>>() {
            @Override public void onResult(List<Announcement> result) {
                if (cb != null) cb.onResult(result.isEmpty() ? null : result.get(0));
            }

            @Override public void onError(String e) {
                if (cb != null) cb.onError(e);
            }
        });
    }

    public void getAnnouncements(String ownerUid, int limit, DbCallback<List<Announcement>> cb) {
        FirebaseFirestore.getInstance()
                .collection(Constants.FIRESTORE_ANNOUNCEMENTS)
                .whereEqualTo("owner_uid", ownerUid)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Announcement> announcements = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        announcements.add(fromDocument(doc));
                    }
                    if (cb != null) cb.onResult(announcements);
                })
                .addOnFailureListener(e -> {
                    Log.e("GenTrack", "Firestore error loading announcements", e);
                    if (cb != null) cb.onError(e.getMessage());
                });
    }

    public void postAnnouncement(String ownerUid, String title, String message,
                                 DbCallback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("owner_uid",  ownerUid);
        data.put("title",      title);
        data.put("message",    message);
        data.put("created_at", FieldValue.serverTimestamp());

        Log.d("GenTrack", "Posting announcement: " + data);
        FirebaseFirestore.getInstance()
                .collection(Constants.FIRESTORE_ANNOUNCEMENTS)
                .add(data)
                .addOnSuccessListener(ref -> {
                    if (cb != null) cb.onResult(null);
                })
                .addOnFailureListener(e -> {
                    Log.e("GenTrack", "Firestore error posting announcement", e);
                    if (cb != null) cb.onError(e.getMessage());
                });
    }

    private Announcement fromDocument(QueryDocumentSnapshot doc) {
        Announcement a = new Announcement();
        a.setId(doc.getId());
        a.setUid(doc.getString("owner_uid"));
        a.setTitle(doc.getString("title"));
        a.setMessage(doc.getString("message"));
        Timestamp ts = doc.getTimestamp("created_at");
        if (ts != null) a.setCreatedAt(ts);
        return a;
    }
}
