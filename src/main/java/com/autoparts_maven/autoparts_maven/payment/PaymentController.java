package com.autoparts_maven.autoparts_maven.payment;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.payment.dto.PaymentDtos.CardPaymentRequest;
import com.autoparts_maven.autoparts_maven.payment.dto.PaymentDtos.PaymentResponse;
import com.autoparts_maven.autoparts_maven.payment.dto.PaymentDtos.PixPaymentRequest;
import com.autoparts_maven.autoparts_maven.payment.gateway.MercadoPagoProperties;
import com.autoparts_maven.autoparts_maven.payment.gateway.WebhookSignatureVerifier;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final MercadoPagoProperties mercadoPagoProperties;

    /** Public: the key the frontend needs to tokenize cards with Mercado Pago's Card Payment Brick. */
    @GetMapping("/api/payments/config")
    public Map<String, String> config() {
        return Map.of("provider", "MERCADO_PAGO", "publicKey", paymentService.publicKey());
    }

    @PostMapping("/api/orders/{orderId}/payments/pix")
    public PaymentResponse payWithPix(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
            @Valid @RequestBody(required = false) PixPaymentRequest request) {
        return paymentService.payWithPix(userId(jwt), orderId, request == null ? null : request.cpf());
    }

    @PostMapping("/api/orders/{orderId}/payments/card")
    public PaymentResponse payWithCard(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
            @Valid @RequestBody CardPaymentRequest request) {
        return paymentService.payWithCard(userId(jwt), orderId, request);
    }

    @GetMapping("/api/orders/{orderId}/payments")
    public List<PaymentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return paymentService.listForOrder(userId(jwt), orderId);
    }

    /** Re-reads the payment from the provider (after a 3DS challenge or while waiting for a Pix). */
    @PostMapping("/api/orders/{orderId}/payments/{paymentId}/sync")
    public PaymentResponse sync(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
            @PathVariable Long paymentId) {
        return paymentService.sync(userId(jwt), orderId, paymentId);
    }

    /**
     * Public notification endpoint, authenticated by the x-signature HMAC. Only "payment" events
     * are processed; the payment is then fetched from Mercado Pago (the body is not trusted).
     */
    @PostMapping("/api/payments/webhooks/mercadopago")
    public ResponseEntity<Void> mercadoPagoWebhook(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataIdParam,
            @RequestParam(name = "type", required = false) String typeParam,
            @RequestBody(required = false) JsonNode body) {
        String dataId = dataIdParam != null ? dataIdParam
                : body == null ? null : body.path("data").path("id").asString(null);
        if (!WebhookSignatureVerifier.isValid(signature, requestId, dataId, mercadoPagoProperties.getWebhookSecret())) {
            log.warn("Rejected Mercado Pago notification with an invalid signature");
            throw new InvalidWebhookSignatureException();
        }
        String type = typeParam != null ? typeParam : body == null ? null : body.path("type").asString(null);
        if ("payment".equals(type) && dataId != null) {
            paymentService.handleNotification(dataId);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/admin/payments/attention")
    public PageResponse<PaymentResponse> needingAttention(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.needingAttention(pageable);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
