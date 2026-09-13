package com.angel.flexbuddy.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.PushConfigResponse;
import com.angel.flexbuddy.dto.PushMessage;
import com.angel.flexbuddy.dto.PushSubscriptionRequest;
import com.angel.flexbuddy.exception.InvalidPushSubscriptionException;
import com.angel.flexbuddy.exception.PushDeliveryException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PushSubscription;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PushSubscriptionRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** The server posts to subscription endpoints, so only browser push services are accepted. */
    private static final List<String> PUSH_SERVICE_HOSTS = List.of(
            ".googleapis.com", ".mozilla.com", ".push.apple.com", ".notify.windows.com");

    private final PushSubscriptionRepository subscriptionRepository;
    private final AppUserRepository userRepository;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PushService(PushSubscriptionRepository subscriptionRepository, AppUserRepository userRepository,
            PushGateway gateway, ObjectMapper objectMapper, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return gateway.isConfigured();
    }

    public PushConfigResponse config() {
        return gateway.isConfigured()
                ? new PushConfigResponse(true, gateway.publicKey())
                : new PushConfigResponse(false, null);
    }

    @Transactional
    public void subscribe(String email, PushSubscriptionRequest request, String userAgent) {
        if (!gateway.isConfigured()) {
            throw new InvalidPushSubscriptionException("Push reminders are not configured on this server.");
        }
        String endpoint = supportedEndpoint(request.endpoint());
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        PushSubscription subscription = subscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(PushSubscription::new);
        if (subscription.getCreatedAt() == null) subscription.setCreatedAt(Instant.now(clock));
        subscription.setOwner(owner);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscription.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())));
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(String email, String endpoint) {
        subscriptionRepository.deleteByEndpointAndOwnerEmailIgnoreCase(endpoint, email);
    }

    /** Sends to every subscription the driver has; a 404 or 410 from the push service removes that subscription. */
    @Transactional
    public int send(AppUser owner, PushMessage message) {
        String payload = objectMapper.writeValueAsString(message);
        int delivered = 0;
        for (PushSubscription subscription : subscriptionRepository.findAllByOwnerId(owner.getId())) {
            try {
                int status = gateway.send(subscription, payload);
                if (status == 404 || status == 410) {
                    subscriptionRepository.delete(subscription);
                } else if (status >= 200 && status < 300) {
                    subscription.setLastUsedAt(Instant.now(clock));
                    delivered++;
                } else {
                    log.warn("Push service answered {} for subscription {}", status, subscription.getId());
                }
            } catch (PushDeliveryException exception) {
                log.warn("Push delivery failed for subscription {}: {}", subscription.getId(), exception.getMessage());
            }
        }
        return delivered;
    }

    static String supportedEndpoint(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint.trim());
        } catch (URISyntaxException exception) {
            throw unsupportedEndpoint();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || PUSH_SERVICE_HOSTS.stream().noneMatch(host::endsWith)) {
            throw unsupportedEndpoint();
        }
        return uri.toString();
    }

    private static InvalidPushSubscriptionException unsupportedEndpoint() {
        return new InvalidPushSubscriptionException("This push endpoint is not from a supported browser push service.");
    }
}
