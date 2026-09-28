package com.angel.flexbuddy.dto;

import java.time.LocalTime;

/**
 * What happened on a block beyond its schedule and pay. When a request carries this object, every value in it
 * replaces the saved one, so a null clears it; when the object is left out, the saved details are kept.
 */
public record BlockDetailsRequest(LocalTime actualStart, LocalTime actualEnd) {

    public static final BlockDetailsRequest EMPTY = new BlockDetailsRequest(null, null);
}
