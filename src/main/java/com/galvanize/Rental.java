package com.galvanize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

class Rental extends Item {
    private LocalDateTime endDate;

    public BigDecimal getRentalPricePerDay() {
        return super.getPrice();
    }
    public LocalDateTime getEndDate() {
        return endDate;
    }
    public Rental(BigDecimal rentalPricePerDay, LocalDateTime endDate) {
        System.out.println(endDate);
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

    @Override
    BigDecimal totalPrice() {
        long days = LocalDateTime.now().until(getEndDate(), ChronoUnit.DAYS) + 1;
        return getRentalPricePerDay().multiply(BigDecimal.valueOf(days));
    }
}
