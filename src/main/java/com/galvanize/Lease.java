package com.galvanize;

import java.math.BigDecimal;
class Lease extends Item {

    private int numberOfMonths;
    private String licensePlate;

    public BigDecimal getPricePerMonth() {
        return super.getPrice();
    }

    public int getNumberOfMonths() {
        return numberOfMonths;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public Lease(String licensePlate, BigDecimal pricePerMonth, int numberOfMonths) {
        this.licensePlate = licensePlate;
        super.setPrice(pricePerMonth);
        this.numberOfMonths = numberOfMonths;
    }

    @Override
    public String toString() {
        return "Lease{" +
                "pricePerMonth=" + super.getPrice() +
                ", numberOfMonths=" + numberOfMonths +
                ", licensePlate='" + licensePlate + '\'' +
                '}';
    }
}
