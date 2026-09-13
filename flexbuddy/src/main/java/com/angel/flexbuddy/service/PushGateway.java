package com.angel.flexbuddy.service;

import com.angel.flexbuddy.model.PushSubscription;

/** Delivers an encrypted Web Push message. Kept behind an interface so reminder logic can be tested without a push service. */
public interface PushGateway {

    boolean isConfigured();

    String publicKey();

    /** Sends the payload and returns the push service's HTTP status code. */
    int send(PushSubscription subscription, String payload);
}
