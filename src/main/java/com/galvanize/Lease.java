package com.galvanize;

import java.math.BigDecimal;

public class Lease {

    private BigDecimal pricePerMonth;
    private int numberOfMonths;
    private String licensePlate;

    public BigDecimal getPricePerMonth() {
        return pricePerMonth;
    }

    public void setPricePerMonth(BigDecimal pricePerMonth) {
        this.pricePerMonth = pricePerMonth;
    }

    public int getNumberOfMonths() {
        return numberOfMonths;
    }

    public void setNumberOfMonths(int numberOfMonths) {
        this.numberOfMonths = numberOfMonths;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public Lease(String licensePlate, BigDecimal pricePerMonth, int numberOfMonths) {
        this.licensePlate = licensePlate;
        this.pricePerMonth = pricePerMonth;
        this.numberOfMonths = numberOfMonths;
    }

    @Override
    public String toString() {
        return "Lease{" +
                "pricePerMonth=" + pricePerMonth +
                ", numberOfMonths=" + numberOfMonths +
                ", licensePlate='" + licensePlate + '\'' +
                '}';
    }
}
