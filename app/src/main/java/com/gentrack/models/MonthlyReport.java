package com.gentrack.models;

public class MonthlyReport {

    private String month;
    private int    totalCustomersBilled;
    private double totalExpectedRevenue;
    private String ownerUid;

    public MonthlyReport() {}

    public MonthlyReport(String month, int totalCustomersBilled,
                         double totalExpectedRevenue, String ownerUid) {
        this.month                = month;
        this.totalCustomersBilled = totalCustomersBilled;
        this.totalExpectedRevenue = totalExpectedRevenue;
        this.ownerUid             = ownerUid;
    }

    public String getMonth()                { return month; }
    public int    getTotalCustomersBilled() { return totalCustomersBilled; }
    public double getTotalExpectedRevenue() { return totalExpectedRevenue; }
    public String getOwnerUid()             { return ownerUid; }

    public void setMonth(String month)                          { this.month = month; }
    public void setTotalCustomersBilled(int totalCustomersBilled) { this.totalCustomersBilled = totalCustomersBilled; }
    public void setTotalExpectedRevenue(double totalExpectedRevenue) { this.totalExpectedRevenue = totalExpectedRevenue; }
    public void setOwnerUid(String ownerUid)                    { this.ownerUid = ownerUid; }

    @Override
    public String toString() {
        return "MonthlyReport{month='" + month + "', revenue=" + totalExpectedRevenue + "}";
    }
}
