package com.galvanize;

import java.math.BigDecimal;

abstract class Item {

	private BigDecimal price;

	public BigDecimal getPrice() {
		return price;
	}
	public void setPrice(BigDecimal price) {
		this.price = price;
	}
}
