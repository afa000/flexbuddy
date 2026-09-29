package com.angel.flexbuddy.dto;

/**
 * The body of a 409. The code is CONFLICT, with the shift as it is now, when a change was made against an older
 * version, or DUPLICATE, with no shift, when two copies of the same create raced and the first one landed.
 */
public record ConflictResponse(String code, ShiftResponse current) {
}
