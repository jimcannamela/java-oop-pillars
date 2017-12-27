package com.galvanize;

import java.math.BigDecimal;

public class Purchase {

    public BigDecimal price;
    public String productName;

    public Purchase(String productName, BigDecimal price) {
        this.productName = productName;
        this.price = price;
    }

    @Override
    public String toString() {
        return "Purchase{" +
                "price=" + price +
                ", productName='" + productName + '\'' +
                '}';
    }
}
