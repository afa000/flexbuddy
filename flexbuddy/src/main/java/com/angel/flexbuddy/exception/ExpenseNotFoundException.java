package com.angel.flexbuddy.exception;

public class ExpenseNotFoundException extends LocalizedException {

    public ExpenseNotFoundException(Long id) {
        super("error.expense.notFound", new Object[] {String.valueOf(id)});
    }
}
