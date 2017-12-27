package com.galvanize;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Rental {

    public BigDecimal rentalPricePerDay;
    public LocalDateTime endDate;

    public Rental(BigDecimal rentalPricePerDay, LocalDateTime endDate) {
        this.rentalPricePerDay = rentalPricePerDay;
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return "Rental{" +
                "rentalPricePerDay=" + rentalPricePerDay +
                ", endDate=" + endDate +
                '}';
    }
}
