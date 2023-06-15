package com.galvanize;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class Rental extends Item {
    private LocalDateTime endDate;

    public BigDecimal getRentalPricePerDay() {
        return super.getPrice();
    }
    public LocalDateTime getEndDate() {
        return endDate;
    }
    public Rental(BigDecimal rentalPricePerDay, LocalDateTime endDate) {
        super.setPrice(rentalPricePerDay);
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return "Rental{" +
                "rentalPricePerDay=" + super.getPrice() +
                ", endDate=" + endDate +
                '}';
    }
}
