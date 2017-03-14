package com.acme.ecommerce.web.exceptions;

public class QuantityExceedsStockException extends RuntimeException {
    public QuantityExceedsStockException() {
        super("Insufficient product in stock");
    }
}
