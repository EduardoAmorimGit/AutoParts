package com.autoparts_maven.autoparts_maven.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validates Mercado Pago's {@code x-signature} header ("ts=...,v1=..."), following the official
 * SDK validator: HMAC-SHA256 (lowercase hex) of the manifest
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} with the webhook secret, where a part is
 * omitted when its value is absent (ts is always present), compared in constant time.
 */
public final class WebhookSignatureVerifier {

    private WebhookSignatureVerifier() {
    }

    public static boolean isValid(String signatureHeader, String requestId, String dataId, String secret) {
        if (signatureHeader == null || secret == null || secret.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2 && keyValue[0].trim().equals("ts")) {
                ts = keyValue[1].trim();
            } else if (keyValue.length == 2 && keyValue[0].trim().equals("v1")) {
                v1 = keyValue[1].trim();
            }
        }
        if (ts == null || ts.isEmpty() || v1 == null || v1.isEmpty()) {
            return false;
        }
        String expected = sign(manifest(dataId, requestId, ts), secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isEmpty()) {
            manifest.append("id:").append(dataId).append(';');
        }
        if (requestId != null && !requestId.isEmpty()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return manifest.toString();
    }

    static String sign(String manifest, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }
}
