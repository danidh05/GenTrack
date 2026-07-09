package com.gentrack.network;

import android.net.Uri;
import android.util.Log;

import com.gentrack.utils.Constants;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class StorageService {

    private static StorageService instance;
    private final FirebaseStorage storage;

    private StorageService() {
        storage = FirebaseStorage.getInstance();
    }

    public static synchronized StorageService getInstance() {
        if (instance == null) instance = new StorageService();
        return instance;
    }

    public void uploadCustomerImage(String ownerUid, int customerId, Uri imageUri, StorageCallback cb) {
        String path = Constants.STORAGE_CUSTOMERS_PATH + "/" + ownerUid + "/"
                + customerId + Constants.STORAGE_IMAGE_EXTENSION;
        StorageReference ref = storage.getReference().child(path);

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            if (cb != null) cb.onSuccess(uri.toString());
                        })
                        .addOnFailureListener(e -> {
                            Log.e("GenTrack", "Failed to resolve download URL", e);
                            if (cb != null) cb.onError("Could not upload photo. Please check your connection and try again.");
                        }))
                .addOnFailureListener(e -> {
                    Log.e("GenTrack", "Failed to upload customer image", e);
                    if (cb != null) cb.onError("Could not upload photo. Please check your connection and try again.");
                });
    }
}
