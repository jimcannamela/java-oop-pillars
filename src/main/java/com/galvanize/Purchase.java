package com.galvanize;

import java.math.BigDecimal;

class Purchase extends Item {

    private String productName;

    public BigDecimal getPrice() {
        return super.getPrice();
    }

    public String getProductName() {
        return productName;
    }


    public Purchase(String productName, BigDecimal price) {
        this.productName = productName;
        super.setPrice(price);
    }

    @Override
    public String toString() {
        return "Purchase{" +
                "price=" + super.getPrice() +
                ", productName='" + productName + '\'' +
                '}';
    }
}
