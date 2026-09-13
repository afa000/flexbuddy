package com.angel.flexbuddy.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;

import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.angel.flexbuddy.exception.PushDeliveryException;
import com.angel.flexbuddy.model.PushSubscription;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/** Web Push over VAPID. Push stays disabled, rather than failing startup, when the keys are missing or invalid. */
@Component
public class WebPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushGateway.class);
    private static final int TIME_TO_LIVE_SECONDS = 12 * 60 * 60;

    private final String publicKey;
    private final PushService pushService;

    public WebPushGateway(@Value("${flexbuddy.push.vapid-public-key:}") String publicKey,
            @Value("${flexbuddy.push.vapid-private-key:}") String privateKey,
            @Value("${flexbuddy.push.subject:https://flexbuddy.onrender.com}") String subject) {
        this.pushService = createService(publicKey, privateKey, subject);
        this.publicKey = pushService == null ? null : publicKey.trim();
    }

    private static PushService createService(String publicKey, String privateKey, String subject) {
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) return null;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(publicKey.trim(), privateKey.trim(), subject);
        } catch (GeneralSecurityException | RuntimeException exception) {
            log.error("Push reminders are disabled because the VAPID keys could not be loaded: {}",
                    exception.getMessage());
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public int send(PushSubscription subscription, String payload) {
        if (pushService == null) throw new IllegalStateException("Push reminders are not configured.");
        try {
            Notification notification = new Notification(subscription.getEndpoint(), subscription.getP256dh(),
                    subscription.getAuth(), payload.getBytes(StandardCharsets.UTF_8), TIME_TO_LIVE_SECONDS);
            HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            return response.getStatusLine().getStatusCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PushDeliveryException("Push delivery was interrupted.", exception);
        } catch (Exception exception) {
            throw new PushDeliveryException(exception.getMessage(), exception);
        }
    }
}
