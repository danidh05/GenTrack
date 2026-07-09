package com.gentrack.models;

import com.google.firebase.Timestamp;

public class Announcement {

    private String    id;
    private String    uid;
    private String    title;
    private String    message;
    private Timestamp createdAt;

    public Announcement() {}

    public Announcement(String id, String uid, String title, String message, Timestamp createdAt) {
        this.id        = id;
        this.uid       = uid;
        this.title     = title;
        this.message   = message;
        this.createdAt = createdAt;
    }

    public String    getId()        { return id; }
    public String    getUid()       { return uid; }
    public String    getTitle()     { return title; }
    public String    getMessage()   { return message; }
    public Timestamp getCreatedAt() { return createdAt; }

    public void setId(String id)             { this.id = id; }
    public void setUid(String uid)           { this.uid = uid; }
    public void setTitle(String title)       { this.title = title; }
    public void setMessage(String message)   { this.message = message; }
    public void setCreatedAt(Timestamp t)    { this.createdAt = t; }

    @Override
    public String toString() {
        return "Announcement{id='" + id + "', title='" + title + "'}";
    }
}
