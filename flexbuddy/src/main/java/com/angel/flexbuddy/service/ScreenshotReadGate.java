package com.angel.flexbuddy.service;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.angel.flexbuddy.exception.ScreenshotBusyException;

/**
 * Screenshots are decoded and read one at a time, so two imports never hold their images and Tesseract's memory
 * together. The decode is inside the turn too: a decoded screenshot is up to 20 MB, and uploads waiting for the reader
 * must not each hold one.
 */
@Component
public class ScreenshotReadGate {

    private final Semaphore permit = new Semaphore(1, true);
    private final Duration wait;

    public ScreenshotReadGate(@Value("${flexbuddy.import.ocr-wait:20s}") Duration wait) {
        this.wait = wait;
    }

    /** Runs the read once it is this upload's turn; an upload that waits longer than the limit is told to try again. */
    public <T> T read(Supplier<T> read) {
        try {
            if (!permit.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ScreenshotBusyException("error.import.busy");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ScreenshotBusyException("error.import.busy");
        }
        try {
            return read.get();
        }
        finally {
            permit.release();
        }
    }

    Semaphore permit() {
        return permit;
    }
}
