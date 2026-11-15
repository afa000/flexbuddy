package com.angel.flexbuddy.api;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.repository.ApiWriteReceiptRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.ShiftImportService;

/**
 * NA-07: reads a screenshot for the native app through the website's {@link ShiftImportService}. Nothing about the
 * screenshot is kept: no receipt, no row, no log line. The screenshot's Idempotency-Key is the one its single save
 * will use, so a key that already saved a block is answered without reading the image again.
 */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiImportService {
    /** The account already has a preview being read; answered as 429 {@code busy_account}. */
    static class AccountBusy extends RuntimeException { }

    /** The key already saved this block; answered as 409 {@code already_saved} with its id. */
    static class AlreadySaved extends RuntimeException {
        private final long id;
        AlreadySaved(long id) { this.id = id; }
        long id() { return id; }
    }

    private final ShiftImportService imports;
    private final ApiWriteReceipts receipts;
    private final ApiWriteReceiptRepository receiptRows;
    private final AppUserRepository users;
    /**
     * Accounts with a preview in flight on this server instance. An entry lives only for one request, so the set holds
     * at most one entry per request being served.
     */
    private final Set<String> reading = ConcurrentHashMap.newKeySet();

    public ApiImportService(ShiftImportService imports, ApiWriteReceipts receipts,
            ApiWriteReceiptRepository receiptRows, AppUserRepository users) {
        this.imports = imports;
        this.receipts = receipts;
        this.receiptRows = receiptRows;
        this.users = users;
    }

    ApiImportPreview preview(String email, String keyHeader, MultipartFile screenshot) {
        String key = receipts.key(keyHeader);
        String account = email.toLowerCase(Locale.ROOT);
        if (!reading.add(account)) throw new AccountBusy();
        try {
            var owner = users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalStateException("Missing account"));
            var previous = receiptRows.findByOwnerIdAndRequestKey(owner.getId(), key);
            if (previous.isPresent()) {
                Long shiftId = previous.get().getShiftId();
                if (shiftId == null) throw new ApiWriteReceipts.KeyConflict();
                throw new AlreadySaved(shiftId);
            }
            return ApiImportPreview.of(imports.createPreview(email, screenshot));
        } finally {
            reading.remove(account);
        }
    }
}
