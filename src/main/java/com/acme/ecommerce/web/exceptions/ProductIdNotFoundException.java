package com.acme.ecommerce.web.exceptions;

public class ProductIdNotFoundException extends RuntimeException {
    public ProductIdNotFoundException() {
        super("Sorry, the product id requested was not found in the database.");
    }
}
