package com.gentrack.network;

public interface StorageCallback {
    void onSuccess(String downloadUrl);
    void onError(String message);
}
