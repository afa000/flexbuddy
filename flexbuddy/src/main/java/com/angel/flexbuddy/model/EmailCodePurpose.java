package com.angel.flexbuddy.model;

/** Why a code was emailed: to confirm a new address, as the second step of signing in, or to confirm a deletion. */
public enum EmailCodePurpose {
    VERIFY_EMAIL,
    SIGN_IN,
    CONFIRM_DELETE
}
