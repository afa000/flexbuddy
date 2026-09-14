package com.angel.flexbuddy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "flexbuddy.security.remember-me-key=integration-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DigitalAssetLinksIntegrationTest {

    private static final String UPLOAD_KEY_FINGERPRINT =
            "A3:EC:82:A5:7E:FD:B3:07:93:B0:C6:88:E2:32:B1:E9:4B:49:53:69:70:46:54:EC:24:29:AB:6A:84:BF:FE:37";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void assetLinksIsPublicAndIdentifiesTheAndroidApp() throws Exception {
        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].relation[0]")
                        .value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.angel.flexbuddy"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]")
                        .value(UPLOAD_KEY_FINGERPRINT));
    }
}
