package com.gentrack.models;

public class Payment {

    private int    id;
    private String ownerUid;
    private int    billId;
    private double amountPaid;
    private String date;
    private double remainingBalance;
    private String createdAt;
    private String updatedAt;

    public Payment() {}

    public Payment(int id, String ownerUid, int billId, double amountPaid,
                   String date, double remainingBalance, String createdAt, String updatedAt) {
        this.id               = id;
        this.ownerUid         = ownerUid;
        this.billId           = billId;
        this.amountPaid       = amountPaid;
        this.date             = date;
        this.remainingBalance = remainingBalance;
        this.createdAt        = createdAt;
        this.updatedAt        = updatedAt;
    }

    public int    getId()               { return id; }
    public String getOwnerUid()         { return ownerUid; }
    public int    getBillId()           { return billId; }
    public double getAmountPaid()       { return amountPaid; }
    public String getDate()             { return date; }
    public double getRemainingBalance() { return remainingBalance; }
    public String getCreatedAt()        { return createdAt; }
    public String getUpdatedAt()        { return updatedAt; }

    public void setId(int id)                          { this.id = id; }
    public void setOwnerUid(String ownerUid)           { this.ownerUid = ownerUid; }
    public void setBillId(int billId)                  { this.billId = billId; }
    public void setAmountPaid(double amountPaid)       { this.amountPaid = amountPaid; }
    public void setDate(String date)                   { this.date = date; }
    public void setRemainingBalance(double rb)         { this.remainingBalance = rb; }
    public void setCreatedAt(String t)                 { this.createdAt = t; }
    public void setUpdatedAt(String t)                 { this.updatedAt = t; }

    @Override
    public String toString() {
        return "Payment{id=" + id + ", billId=" + billId + ", amountPaid=" + amountPaid
                + ", remainingBalance=" + remainingBalance + "}";
    }
}
