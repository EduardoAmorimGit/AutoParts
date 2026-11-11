package com.autoparts_maven.autoparts_maven.payment.gateway;

/**
 * The provider could not process the request. {@code unavailable} distinguishes temporary
 * problems (timeouts, 5xx: retry later) from requests the provider refused (4xx).
 */
public class PaymentGatewayException extends RuntimeException {

    private final boolean unavailable;

    public PaymentGatewayException(String message, boolean unavailable, Throwable cause) {
        super(message, cause);
        this.unavailable = unavailable;
    }

    public boolean isUnavailable() {
        return unavailable;
    }
}
