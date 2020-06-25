## Java OOP Checkpoint: Pillars of OOP

This Order management application contains the following classes:

- `Lease`
- `Purchase`
- `Rental`
- `Order`

The code works like this:

```java
// A Lease's total cost is the amount * the number of months
// $5.00 * 12 = 60
Lease lease = new Lease("ADE-528", new BigDecimal("5.00"), 12);

// A purchase's total cost is just the amount
// $30.00
Purchase purchase = new Purchase("Lawn Mower", new BigDecimal("30.00"));

// A rental's total cost is the amount * the number of days
// $7.00 * 7 = $49.00
Rental rental = new Rental(new BigDecimal("7.00"), LocalDateTime.now().plus(7, ChronoUnit.DAYS));

// You can add these to an order...
Order order = new Order();
order.addItem(lease);
order.addItem(purchase);
order.addItem(rental);

// And get the total...
order.getTotal(); // $60 + $30 + $49 = $139
```

The app also already has a passing test suite. Run using gradle:
```sh
$ ./gradlew clean test
```
