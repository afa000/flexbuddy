package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.PushMessage;
import com.angel.flexbuddy.dto.PushSubscriptionRequest;
import com.angel.flexbuddy.exception.InvalidPushSubscriptionException;
import com.angel.flexbuddy.exception.PushDeliveryException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PushSubscription;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PushSubscriptionRepository;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final Instant NOW = Instant.parse("2026-09-12T15:00:00Z");
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc123";

    @Mock PushSubscriptionRepository subscriptionRepository;
    @Mock AppUserRepository userRepository;
    @Mock PushGateway gateway;

    private PushService service;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        service = new PushService(subscriptionRepository, userRepository, gateway, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        owner = new AppUser("Angel", EMAIL, "hash");
        owner.setId(3L);
    }

    @Test
    void subscribe_updatesTheExistingRowForAnEndpoint() {
        when(gateway.isConfigured()).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));
        PushSubscription existing = subscription(9L);
        existing.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        when(subscriptionRepository.findByEndpoint(FCM)).thenReturn(Optional.of(existing));

        service.subscribe(EMAIL, request(FCM), "Mozilla/5.0");

        verify(subscriptionRepository).save(existing);
        assertThat(existing.getOwner()).isSameAs(owner);
        assertThat(existing.getP256dh()).isEqualTo("new-key");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
        assertThat(existing.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(existing.getCreatedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void subscribe_createsARowForANewEndpoint() {
        when(gateway.isConfigured()).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findByEndpoint(FCM)).thenReturn(Optional.empty());

        service.subscribe(EMAIL, request(FCM), null);

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getEndpoint()).isEqualTo(FCM);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void subscribe_rejectsEndpointsThatAreNotBrowserPushServices() {
        when(gateway.isConfigured()).thenReturn(true);

        for (String endpoint : List.of("http://fcm.googleapis.com/fcm/send/x", "https://169.254.169.254/latest",
                "https://evil.example/fcm.googleapis.com", "https://user@fcm.googleapis.com/x", "not a url")) {
            assertThatThrownBy(() -> service.subscribe(EMAIL, request(endpoint), null))
                    .isInstanceOf(InvalidPushSubscriptionException.class);
        }
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_isRefusedWhenPushIsNotConfigured() {
        when(gateway.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.subscribe(EMAIL, request(FCM), null))
                .isInstanceOf(InvalidPushSubscriptionException.class)
                .hasMessage("Push reminders are not configured on this server.");
    }

    @Test
    void send_removesSubscriptionsThePushServiceReportsGone() {
        PushSubscription gone = subscription(1L);
        PushSubscription active = subscription(2L);
        when(subscriptionRepository.findAllByOwnerId(3L)).thenReturn(List.of(gone, active));
        when(gateway.send(eq(gone), anyString())).thenReturn(410);
        when(gateway.send(eq(active), anyString())).thenReturn(201);

        int delivered = service.send(owner, message());

        assertThat(delivered).isEqualTo(1);
        verify(subscriptionRepository).delete(gone);
        verify(subscriptionRepository, never()).delete(active);
        assertThat(active.getLastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void send_keepsGoingWhenOneDeliveryFailsAndSendsJson() {
        PushSubscription broken = subscription(1L);
        PushSubscription active = subscription(2L);
        when(subscriptionRepository.findAllByOwnerId(3L)).thenReturn(List.of(broken, active));
        when(gateway.send(eq(broken), anyString())).thenThrow(new PushDeliveryException("timeout", null));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(gateway.send(eq(active), payload.capture())).thenReturn(201);

        assertThat(service.send(owner, message())).isEqualTo(1);
        verify(subscriptionRepository, never()).delete(any());
        assertThat(payload.getValue()).contains("\"title\":\"Upcoming block · VEA7\"", "\"url\":\"/?screen=schedule\"",
                "\"tag\":\"shift-7-start\"");
    }

    private PushSubscriptionRequest request(String endpoint) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys("new-key", "new-auth"));
    }

    private PushSubscription subscription(Long id) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setEndpoint(FCM + id);
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        return subscription;
    }

    private PushMessage message() {
        return new PushMessage("Upcoming block · VEA7", "Starts at 3:15 PM", "/?screen=schedule", "shift-7-start");
    }
}
