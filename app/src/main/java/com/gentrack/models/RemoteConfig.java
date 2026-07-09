package com.gentrack.models;

public class RemoteConfig {

    private double defaultPricePerAmp;
    private double generatorCapacity;
    private double price5a;
    private double price10a;
    private double price15a;
    private double pricePerKwh;
    private String ownerUid;

    public RemoteConfig() {}

    public double getDefaultPricePerAmp() { return defaultPricePerAmp; }
    public double getGeneratorCapacity()  { return generatorCapacity; }
    public double getPrice5a()            { return price5a; }
    public double getPrice10a()           { return price10a; }
    public double getPrice15a()           { return price15a; }
    public double getPricePerKwh()        { return pricePerKwh; }
    public String getOwnerUid()           { return ownerUid; }

    public void setDefaultPricePerAmp(double v) { this.defaultPricePerAmp = v; }
    public void setGeneratorCapacity(double v)   { this.generatorCapacity = v; }
    public void setPrice5a(double v)             { this.price5a = v; }
    public void setPrice10a(double v)            { this.price10a = v; }
    public void setPrice15a(double v)            { this.price15a = v; }
    public void setPricePerKwh(double v)         { this.pricePerKwh = v; }
    public void setOwnerUid(String ownerUid)     { this.ownerUid = ownerUid; }

    @Override
    public String toString() {
        return "RemoteConfig{defaultPricePerAmp=" + defaultPricePerAmp
                + ", price5a=" + price5a + ", price10a=" + price10a
                + ", price15a=" + price15a + ", pricePerKwh=" + pricePerKwh + "}";
    }
}
