package com.angel.flexbuddy.exception;

public class TaxPaymentNotFoundException extends RuntimeException {
    public TaxPaymentNotFoundException(Long id) {
        super("Tax payment " + id + " was not found.");
    }
}
