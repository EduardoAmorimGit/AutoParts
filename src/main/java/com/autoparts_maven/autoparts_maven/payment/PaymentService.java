package com.autoparts_maven.autoparts_maven.payment;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.order.OrderService;
import com.autoparts_maven.autoparts_maven.order.OrderService.PayableOrder;
import com.autoparts_maven.autoparts_maven.order.OrderStatus;
import com.autoparts_maven.autoparts_maven.payment.dto.PaymentDtos.CardPaymentRequest;
import com.autoparts_maven.autoparts_maven.payment.dto.PaymentDtos.PaymentResponse;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.CardCharge;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.GatewayPayment;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.Payer;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGateway.PixCharge;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGatewayException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Charges orders through the {@link PaymentGateway}. The provider call never runs inside a
 * database transaction: (1) lock the order, validate and record the attempt, (2) call the
 * provider, (3) record the outcome. The provider's payment status is the only source of truth;
 * notifications just trigger a re-read.
 */
@Service
public class PaymentService {

    static final String EXTERNAL_REFERENCE_PREFIX = "autoparts-payment-";
    private static final Pattern EXTERNAL_REFERENCE = Pattern.compile("^" + EXTERNAL_REFERENCE_PREFIX + "(\\d+)$");
    // Mercado Pago accepts Pix expirations between 30 minutes and 30 days.
    private static final Duration PIX_MIN_EXPIRATION = Duration.ofMinutes(31);
    // The order stays open a little longer than its Pix, so a last-second payment still finds it.
    private static final Duration ORDER_GRACE = Duration.ofMinutes(5);
    private static final Duration IN_FLIGHT_WINDOW = Duration.ofMinutes(2);
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final Optional<PaymentGateway> gateway;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final AuthService authService;
    private final TransactionTemplate transactions;
    private final int maxInstallments;

    public PaymentService(Optional<PaymentGateway> gateway, PaymentRepository paymentRepository,
            OrderService orderService, AuthService authService, TransactionTemplate transactions,
            @Value("${payments.max-installments:1}") int maxInstallments) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.authService = authService;
        this.transactions = transactions;
        this.maxInstallments = maxInstallments;
    }

    public String publicKey() {
        return gateway().publicKey();
    }

    /** Creates (or returns the still valid) Pix charge of an order awaiting payment. */
    public PaymentResponse payWithPix(Long userId, Long orderId, String cpf) {
        PaymentGateway provider = gateway();
        Instant now = Instant.now();
        Payment attempt = transactions.execute(status -> {
            PayableOrder order = requirePayable(orderService.lockForPayment(userId, orderId));
            List<Payment> payments = paymentRepository.findByOrderIdOrderByIdAsc(orderId);
            Optional<Payment> reusable = payments.stream()
                    .filter(p -> p.getMethod() == Payment.Method.PIX && p.getStatus() == PaymentStatus.PENDING
                            && p.getProviderPaymentId() != null && p.getExpiresAt() != null
                            && p.getExpiresAt().isAfter(now.plus(Duration.ofMinutes(2))))
                    .findFirst();
            if (reusable.isPresent()) {
                return reusable.get();
            }
            rejectInFlight(payments, now);
            Instant expiresAt = now.plus(PIX_MIN_EXPIRATION);
            if (order.paymentDueAt().isAfter(expiresAt)) {
                expiresAt = order.paymentDueAt();
            }
            return newAttempt(order, Payment.Method.PIX, expiresAt);
        });
        if (attempt.getProviderPaymentId() != null) {
            return PaymentResponse.from(attempt, null);
        }

        GatewayPayment result;
        try {
            result = provider.createPix(new PixCharge(attempt.getIdempotencyKey(), externalReference(attempt),
                    attempt.getAmount(), description(orderId), payer(userId, cpf == null ? null : "CPF", cpf),
                    attempt.getExpiresAt()));
        } catch (PaymentGatewayException exception) {
            abandon(attempt.getId());
            throw exception;
        }
        Payment updated = transactions.execute(status -> {
            Payment payment = applyProviderState(attempt.getId(), result);
            orderService.extendPaymentDue(orderId, payment.getExpiresAt() == null
                    ? attempt.getExpiresAt().plus(ORDER_GRACE) : payment.getExpiresAt().plus(ORDER_GRACE));
            return payment;
        });
        return PaymentResponse.from(updated, null);
    }

    /** Charges a card tokenized in the browser (the card data never reaches this server). */
    public PaymentResponse payWithCard(Long userId, Long orderId, CardPaymentRequest request) {
        PaymentGateway provider = gateway();
        if (request.installments() < 1 || request.installments() > maxInstallments) {
            throw new InvalidPaymentRequestException("installments must be between 1 and " + maxInstallments);
        }
        Instant now = Instant.now();
        Payment attempt = transactions.execute(status -> {
            PayableOrder order = requirePayable(orderService.lockForPayment(userId, orderId));
            rejectInFlight(paymentRepository.findByOrderIdOrderByIdAsc(orderId), now);
            return newAttempt(order, Payment.Method.CREDIT_CARD, null);
        });

        GatewayPayment result;
        try {
            result = provider.createCard(new CardCharge(attempt.getIdempotencyKey(), externalReference(attempt),
                    attempt.getAmount(), description(orderId),
                    payer(userId, request.identificationType(), request.identificationNumber()),
                    request.token(), request.paymentMethodId(), request.issuerId(), request.installments()));
        } catch (PaymentGatewayException exception) {
            abandon(attempt.getId());
            throw exception;
        }
        Payment updated = transactions.execute(status -> applyProviderState(attempt.getId(), result));
        return PaymentResponse.from(updated, result.challenge());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(Long userId, Long orderId) {
        orderService.getMine(userId, orderId);
        return paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(payment -> PaymentResponse.from(payment, null)).toList();
    }

    /** Re-reads a payment from the provider (after a 3DS challenge, or when a notification is late). */
    public PaymentResponse sync(Long userId, Long orderId, Long paymentId) {
        orderService.getMine(userId, orderId);
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getOrderId().equals(orderId))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getProviderPaymentId() == null) {
            return PaymentResponse.from(payment, null);
        }
        GatewayPayment result = gateway().fetch(payment.getProviderPaymentId());
        return PaymentResponse.from(transactions.execute(status -> applyProviderState(paymentId, result)), null);
    }

    /**
     * Handles a provider notification. The body is never trusted: the payment is fetched from the
     * provider and matched to our attempt by its external reference.
     */
    public void handleNotification(String providerPaymentId) {
        GatewayPayment result = gateway().fetch(providerPaymentId);
        Long localId = localPaymentId(result.externalReference());
        if (localId == null || !paymentRepository.existsById(localId)) {
            log.warn("Ignoring notification for unknown payment {}", providerPaymentId);
            return;
        }
        transactions.executeWithoutResult(status -> applyProviderState(localId, result));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> needingAttention(Pageable pageable) {
        return PageResponse.from(paymentRepository.findByNeedsAttentionTrue(pageable)
                .map(payment -> PaymentResponse.from(payment, null)));
    }

    /**
     * Applies the provider state to our attempt (caller holds a transaction). An approval pays
     * the order only when the amount matches and the order still awaits payment; a payment that
     * arrives for a cancelled or already paid order is refunded and flagged for the admins.
     */
    Payment applyProviderState(Long paymentId, GatewayPayment result) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
        PayableOrder order = orderService.lockForPayment(payment.getOrderId());
        PaymentStatus previous = payment.getStatus();
        PaymentStatus status = PaymentStatus.fromMercadoPago(result.status());

        payment.setProviderPaymentId(result.id());
        payment.setProviderStatus(result.status());
        payment.setProviderStatusDetail(result.statusDetail());
        payment.setStatus(status);
        if (result.pixQrCode() != null) {
            payment.setPixQrCode(result.pixQrCode());
            payment.setPixQrCodeBase64(result.pixQrCodeBase64());
            payment.setPixTicketUrl(result.pixTicketUrl());
        }
        if (result.expiresAt() != null) {
            payment.setExpiresAt(result.expiresAt());
        }

        if (status == PaymentStatus.APPROVED && previous != PaymentStatus.APPROVED) {
            payment.setApprovedAt(Instant.now());
            if (result.amount() == null || result.amount().compareTo(payment.getAmount()) != 0) {
                payment.flag("Approved amount " + result.amount() + " differs from order amount " + payment.getAmount());
            } else if (!orderService.markPaid(order.id())) {
                refundLatePayment(payment, order);
            }
        } else if (status == PaymentStatus.CHARGED_BACK && previous != PaymentStatus.CHARGED_BACK) {
            payment.flag("Chargeback opened by the cardholder");
        } else if (status == PaymentStatus.REFUNDED && payment.getRefundedAt() == null) {
            payment.setRefundedAt(Instant.now());
        }
        return paymentRepository.saveAndFlush(payment);
    }

    // The money arrived but the order was cancelled/expired or already paid by another attempt.
    private void refundLatePayment(Payment payment, PayableOrder order) {
        String reason = order.status() == OrderStatus.CANCELLED
                ? "Payment approved after the order was cancelled"
                : "Duplicate payment for an order already " + order.status();
        try {
            GatewayPayment refunded = gateway().refund(payment.getProviderPaymentId(), UUID.randomUUID().toString());
            payment.setStatus(PaymentStatus.fromMercadoPago(refunded.status()));
            payment.setProviderStatus(refunded.status());
            payment.setRefundedAt(Instant.now());
            payment.flag(reason + "; refunded automatically");
        } catch (PaymentGatewayException exception) {
            payment.flag(reason + "; AUTOMATIC REFUND FAILED - refund manually");
        }
        log.warn("Payment {} for order {}: {}", payment.getId(), order.id(), reason);
    }

    private Payment newAttempt(PayableOrder order, Payment.Method method, Instant expiresAt) {
        Payment payment = new Payment();
        payment.setOrderId(order.id());
        payment.setProvider(Payment.Provider.MERCADO_PAGO);
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.total());
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setExpiresAt(expiresAt);
        return paymentRepository.saveAndFlush(payment);
    }

    // An attempt recorded but not answered by the provider yet: a second click must not charge twice.
    private static void rejectInFlight(List<Payment> payments, Instant now) {
        boolean inFlight = payments.stream().anyMatch(p -> p.getProviderPaymentId() == null
                && p.getStatus() == PaymentStatus.PENDING && p.getCreatedAt() != null
                && p.getCreatedAt().isAfter(now.minus(IN_FLIGHT_WINDOW)));
        if (inFlight) {
            throw new PaymentInProgressException();
        }
    }

    private void abandon(Long paymentId) {
        transactions.executeWithoutResult(status -> paymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setProviderStatusDetail("creation_failed");
        }));
    }

    private static PayableOrder requirePayable(PayableOrder order) {
        if (order.status() != OrderStatus.AWAITING_PAYMENT) {
            throw new OrderNotPayableException(order.id(), order.status());
        }
        return order;
    }

    private Payer payer(Long userId, String identificationType, String identificationNumber) {
        String digits = identificationNumber == null ? null : identificationNumber.replaceAll("\\D", "");
        return new Payer(authService.me(userId).email(), identificationType, digits);
    }

    private PaymentGateway gateway() {
        return gateway.orElseThrow(PaymentsDisabledException::new);
    }

    static String externalReference(Payment payment) {
        return EXTERNAL_REFERENCE_PREFIX + payment.getId();
    }

    static Long localPaymentId(String externalReference) {
        if (externalReference == null) {
            return null;
        }
        Matcher matcher = EXTERNAL_REFERENCE.matcher(externalReference);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private static String description(Long orderId) {
        return "AutoParts - pedido #" + orderId;
    }
}
