package com.autoparts_maven.autoparts_maven.payment;

import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** In-memory stand-in for Mercado Pago: tests decide the next outcome and can change a payment later. */
public class FakePaymentGateway implements PaymentGateway {

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }
    }

    private final AtomicLong ids = new AtomicLong(1000);
    private final Map<String, GatewayPayment> payments = new ConcurrentHashMap<>();
    final List<String> refunded = new ArrayList<>();
    int createCalls;
    String nextStatus = "pending";
    String nextStatusDetail = "pending_waiting_transfer";
    BigDecimal amountOverride;
    Challenge nextChallenge;

    void reset() {
        payments.clear();
        refunded.clear();
        createCalls = 0;
        nextStatus = "pending";
        nextStatusDetail = "pending_waiting_transfer";
        amountOverride = null;
        nextChallenge = null;
    }

    @Override
    public GatewayPayment createPix(PixCharge charge) {
        createCalls++;
        return store(charge.externalReference(), charge.amount(), "00020126-fake-pix", "iVBORw0KGgo=",
                "https://example.test/ticket", charge.expiresAt(), null);
    }

    @Override
    public GatewayPayment createCard(CardCharge charge) {
        createCalls++;
        return store(charge.externalReference(), charge.amount(), null, null, null, null, nextChallenge);
    }

    @Override
    public GatewayPayment fetch(String paymentId) {
        GatewayPayment payment = payments.get(paymentId);
        if (payment == null) {
            return new GatewayPayment(paymentId, "approved", "accredited", BigDecimal.TEN, "someone-else-42",
                    null, null, null, null, null);
        }
        return payment;
    }

    @Override
    public GatewayPayment refund(String paymentId, String idempotencyKey) {
        refunded.add(paymentId);
        return change(paymentId, "refunded", "refunded");
    }

    @Override
    public String publicKey() {
        return "TEST-fake-public-key";
    }

    /** Provider id of the charge created for our payment (matched by its external reference). */
    String providerIdFor(Object localPaymentId) {
        String reference = "autoparts-payment-" + localPaymentId;
        return payments.values().stream().filter(p -> reference.equals(p.externalReference()))
                .map(GatewayPayment::id).findFirst().orElseThrow();
    }

    /** Simulates the provider changing a payment later (e.g. the customer paid the Pix). */
    GatewayPayment change(String paymentId, String status, String detail) {
        GatewayPayment old = payments.get(paymentId);
        GatewayPayment changed = new GatewayPayment(old.id(), status, detail, old.amount(), old.externalReference(),
                old.pixQrCode(), old.pixQrCodeBase64(), old.pixTicketUrl(), old.expiresAt(), null);
        payments.put(paymentId, changed);
        return changed;
    }

    private GatewayPayment store(String reference, BigDecimal amount, String qr, String qrBase64, String ticket,
            Instant expiresAt, Challenge challenge) {
        String id = String.valueOf(ids.incrementAndGet());
        GatewayPayment payment = new GatewayPayment(id, nextStatus, nextStatusDetail,
                amountOverride != null ? amountOverride : amount, reference, qr, qrBase64, ticket, expiresAt, challenge);
        payments.put(id, payment);
        return payment;
    }
}
