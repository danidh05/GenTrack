package com.gentrack.db;

public interface DbCallback<T> {
    void onResult(T result);
    void onError(String error);
}
