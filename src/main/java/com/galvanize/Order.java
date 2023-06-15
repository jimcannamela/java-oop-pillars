package com.galvanize;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Order {

    private ArrayList<Object> items = new ArrayList<>();
    private BigDecimal total = new BigDecimal("0.00");

    public ArrayList<Object> getItems() {
        return items;
    }
    void addItem(Item item) {
        items.add(item);
        total = total.add(item.totalPrice());
    }
    public BigDecimal getTotal() {
        return total;
    }
}
