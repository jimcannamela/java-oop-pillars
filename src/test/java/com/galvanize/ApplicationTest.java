package com.galvanize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class ApplicationTest {

    @Test
    public void passes() {
        Lease lease = new Lease("XVB-104", new BigDecimal("3.05"), 6);
        assertEquals("Lease{pricePerMonth=3.05, numberOfMonths=6, licensePlate='XVB-104'}", lease.toString());

        Purchase purchase = new Purchase("Phone Case", new BigDecimal("7.88"));
        assertEquals("Purchase{price=7.88, productName='Phone Case'}", purchase.toString());

        Rental rental = new Rental(new BigDecimal("12.44"), LocalDateTime.now().plus(Duration.ofDays(5)));
        assertEquals(true, rental.toString().contains("rentalPricePerDay=12.44"));

        Order order = new Order();
        assertEquals(0, order.items.size());
        assertEquals(new BigDecimal("0.00"), order.getTotal());

        order.addItem(lease);
        assertEquals(1, order.items.size());
        assertEquals(new BigDecimal("18.30"), order.getTotal());

        order.addItem(purchase);
        assertEquals(2, order.items.size());
        assertEquals(new BigDecimal("26.18"), order.getTotal());

        order.addItem(rental);
        assertEquals(3, order.items.size());
        assertEquals(new BigDecimal("88.38"), order.getTotal());
    }

}
