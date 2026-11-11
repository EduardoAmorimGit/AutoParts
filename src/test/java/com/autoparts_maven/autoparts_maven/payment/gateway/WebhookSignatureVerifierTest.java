package com.autoparts_maven.autoparts_maven.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";

    @Test
    void acceptsTheOfficialManifestFormat() throws Exception {
        String v1 = hmac("id:123456;request-id:req-1;ts:1704908010;");

        assertThat(WebhookSignatureVerifier.isValid("ts=1704908010,v1=" + v1, "req-1", "123456", SECRET)).isTrue();
        assertThat(WebhookSignatureVerifier.isValid(" ts=1704908010 , v1=" + v1.toUpperCase(), "req-1", "123456", SECRET))
                .isTrue();
    }

    @Test
    void omitsMissingPartsButAlwaysIncludesTimestamp() throws Exception {
        String v1 = hmac("id:123456;ts:1704908010;");

        assertThat(WebhookSignatureVerifier.isValid("ts=1704908010,v1=" + v1, null, "123456", SECRET)).isTrue();
    }

    @Test
    void rejectsTamperedOrIncompleteSignatures() throws Exception {
        String v1 = hmac("id:123456;request-id:req-1;ts:1704908010;");

        assertThat(WebhookSignatureVerifier.isValid("ts=1704908010,v1=" + v1, "req-1", "999999", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid("ts=1704908011,v1=" + v1, "req-1", "123456", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid("ts=1704908010,v1=" + v1, "req-1", "123456", "other")).isFalse();
        assertThat(WebhookSignatureVerifier.isValid("v1=" + v1, "req-1", "123456", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid(null, "req-1", "123456", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid("ts=1704908010,v1=" + v1, "req-1", "123456", "")).isFalse();
    }

    private static String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
