package com.gentrack.models;

public class Bill {

    private int    id;
    private String ownerUid;
    private int    customerId;
    private String month;
    private int    amps;
    private double pricePerAmp;
    private double total;
    private double previousBalance;
    private double finalTotal;
    private String status;
    private double currentReading;
    private double previousReading;
    private double consumption;
    private String billingModel;
    private String createdAt;
    private String updatedAt;
    // Not persisted — populated by JOIN queries for UI display only
    private double transientTotalPaid;

    public Bill() {}

    public Bill(int id, String ownerUid, int customerId, String month, int amps,
                double pricePerAmp, double total, double previousBalance, double finalTotal,
                String status, String createdAt, String updatedAt,
                double currentReading, double previousReading, double consumption,
                String billingModel) {
        this.id              = id;
        this.ownerUid        = ownerUid;
        this.customerId      = customerId;
        this.month           = month;
        this.amps            = amps;
        this.pricePerAmp     = pricePerAmp;
        this.total           = total;
        this.previousBalance = previousBalance;
        this.finalTotal      = finalTotal;
        this.status          = status;
        this.createdAt       = createdAt;
        this.updatedAt       = updatedAt;
        this.currentReading  = currentReading;
        this.previousReading = previousReading;
        this.consumption     = consumption;
        this.billingModel    = billingModel;
    }

    public int    getId()              { return id; }
    public String getOwnerUid()        { return ownerUid; }
    public int    getCustomerId()      { return customerId; }
    public String getMonth()           { return month; }
    public int    getAmps()            { return amps; }
    public double getPricePerAmp()     { return pricePerAmp; }
    public double getTotal()           { return total; }
    public double getPreviousBalance() { return previousBalance; }
    public double getFinalTotal()      { return finalTotal; }
    public String getStatus()          { return status; }
    public double getCurrentReading()  { return currentReading; }
    public double getPreviousReading() { return previousReading; }
    public double getConsumption()     { return consumption; }
    public String getBillingModel()    { return billingModel; }
    public String getCreatedAt()          { return createdAt; }
    public String getUpdatedAt()          { return updatedAt; }
    public double getTransientTotalPaid() { return transientTotalPaid; }

    public void setId(int id)                        { this.id = id; }
    public void setOwnerUid(String ownerUid)         { this.ownerUid = ownerUid; }
    public void setCustomerId(int customerId)        { this.customerId = customerId; }
    public void setMonth(String month)               { this.month = month; }
    public void setAmps(int amps)                    { this.amps = amps; }
    public void setPricePerAmp(double pricePerAmp)   { this.pricePerAmp = pricePerAmp; }
    public void setTotal(double total)               { this.total = total; }
    public void setPreviousBalance(double pb)        { this.previousBalance = pb; }
    public void setFinalTotal(double finalTotal)     { this.finalTotal = finalTotal; }
    public void setStatus(String status)             { this.status = status; }
    public void setCurrentReading(double v)          { this.currentReading = v; }
    public void setPreviousReading(double v)         { this.previousReading = v; }
    public void setConsumption(double v)             { this.consumption = v; }
    public void setBillingModel(String v)            { this.billingModel = v; }
    public void setCreatedAt(String t)               { this.createdAt = t; }
    public void setUpdatedAt(String t)               { this.updatedAt = t; }
    public void setTransientTotalPaid(double v)      { this.transientTotalPaid = v; }

    @Override
    public String toString() {
        return "Bill{id=" + id + ", month='" + month + "', finalTotal=" + finalTotal
                + ", status='" + status + "', model='" + billingModel + "'}";
    }
}
