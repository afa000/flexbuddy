package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;

/** Runs the Bouncy Castle code behind push reminders with made-up keys and no network. */
class WebPushGatewayTest {

    private static final String SUBJECT = "https://flexbuddy.example.com";
    private static final String ENDPOINT = "https://push.example.com/send/abc123";

    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void generatedVapidKeysConfigurePush() throws Exception {
        KeyPair vapid = newKeyPair();

        WebPushGateway gateway = new WebPushGateway(" " + publicKey(vapid) + " ", privateKey(vapid), SUBJECT);

        assertThat(gateway.isConfigured()).isTrue();
        assertThat(gateway.publicKey()).isEqualTo(publicKey(vapid));
    }

    @Test
    void invalidVapidKeysLeavePushDisabled() {
        WebPushGateway gateway = new WebPushGateway("not-a-key", "also-not-a-key", SUBJECT);

        assertThat(gateway.isConfigured()).isFalse();
        assertThat(gateway.publicKey()).isNull();
    }

    @Test
    void payloadEncryptsIntoVapidSignedRequest() throws Exception {
        KeyPair vapid = newKeyPair();
        KeyPair browser = newKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] payload = "{\"title\":\"Shift starts soon\"}".getBytes(StandardCharsets.UTF_8);
        PushService pushService = new PushService(publicKey(vapid), privateKey(vapid), SUBJECT);
        Notification notification = new Notification(ENDPOINT, publicKey(browser), encode(auth), payload, 60);

        HttpPost post = pushService.preparePost(notification, Encoding.AES128GCM);

        assertThat(post.getURI()).hasToString(ENDPOINT);
        assertThat(post.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
        assertThat(post.getFirstHeader("Authorization").getValue()).startsWith("vapid t=");
        assertThat(post.getEntity().getContentLength()).isGreaterThan(payload.length);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }

    private static String publicKey(KeyPair keyPair) {
        return encode(Utils.encode((ECPublicKey) keyPair.getPublic()));
    }

    private static String privateKey(KeyPair keyPair) {
        return encode(Utils.encode((ECPrivateKey) keyPair.getPrivate()));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
