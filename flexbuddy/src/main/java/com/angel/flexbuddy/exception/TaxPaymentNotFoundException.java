package com.angel.flexbuddy.exception;

public class TaxPaymentNotFoundException extends LocalizedException {

    public TaxPaymentNotFoundException(Long id) {
        super("error.taxPayment.notFound", new Object[] {String.valueOf(id)});
    }
}
