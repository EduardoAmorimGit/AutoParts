package com.autoparts_maven.autoparts_maven.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials come from environment variables only (MERCADOPAGO_*); never commit them. */
@ConfigurationProperties(prefix = "mercadopago")
public class MercadoPagoProperties {

    private String baseUrl = "https://api.mercadopago.com";
    private String accessToken = "";
    private String publicKey = "";
    private String webhookSecret = "";
    /** Optional per-payment notification URL; the panel webhook configuration also works. */
    private String notificationUrl = "";
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 15;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
