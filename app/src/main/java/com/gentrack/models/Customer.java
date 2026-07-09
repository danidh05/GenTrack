package com.gentrack.models;

public class Customer {

    private int    id;
    private String ownerUid;
    private String name;
    private String phone;
    private String location;
    private int    amps;
    private String status;
    private String notes;
    private String imageUrl;
    private String createdAt;
    private String updatedAt;

    public Customer() {}

    public Customer(int id, String ownerUid, String name, String phone, String location,
                    int amps, String status, String notes, String imageUrl,
                    String createdAt, String updatedAt) {
        this.id        = id;
        this.ownerUid  = ownerUid;
        this.name      = name;
        this.phone     = phone;
        this.location  = location;
        this.amps      = amps;
        this.status    = status;
        this.notes     = notes;
        this.imageUrl  = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int    getId()        { return id; }
    public String getOwnerUid()  { return ownerUid; }
    public String getName()      { return name; }
    public String getPhone()     { return phone; }
    public String getLocation()  { return location; }
    public int    getAmps()      { return amps; }
    public String getStatus()    { return status; }
    public String getNotes()     { return notes; }
    public String getImageUrl()  { return imageUrl; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public void setId(int id)               { this.id = id; }
    public void setOwnerUid(String ownerUid){ this.ownerUid = ownerUid; }
    public void setName(String name)        { this.name = name; }
    public void setPhone(String phone)      { this.phone = phone; }
    public void setLocation(String loc)     { this.location = loc; }
    public void setAmps(int amps)           { this.amps = amps; }
    public void setStatus(String status)    { this.status = status; }
    public void setNotes(String notes)      { this.notes = notes; }
    public void setImageUrl(String url)     { this.imageUrl = url; }
    public void setCreatedAt(String t)      { this.createdAt = t; }
    public void setUpdatedAt(String t)      { this.updatedAt = t; }

    @Override
    public String toString() {
        return "Customer{id=" + id + ", name='" + name + "', status='" + status + "', amps=" + amps + "}";
    }
}
