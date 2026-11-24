package com.autoparts_maven.autoparts_maven.order.dto;

import com.autoparts_maven.autoparts_maven.order.Order;
import com.autoparts_maven.autoparts_maven.order.OrderItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** {@code acceptedTermsVersion}: the legal documents version (GET /api/legal/documents) the customer accepted. */
    public record CheckoutRequest(@NotNull Long addressId, @NotBlank @Size(max = 20) String acceptedTermsVersion) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    /** Admin cancellation: the reason is mandatory and shown to the customer. */
    public record AdminCancelRequest(@NotBlank @Size(max = 200) String reason) {
    }

    /** Courier tracking link (https), e.g. the Lalamove share link. */
    public record ShipRequest(
            @NotBlank @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https link")
            String trackingUrl) {
    }

    public record OrderItemResponse(Long id, Long productId, String sku, String productName, BigDecimal unitPrice,
            int quantity, BigDecimal lineTotal) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getId(), item.getProductId(), item.getSku(), item.getProductName(),
                    item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
        }
    }

    public record DeliveryAddress(String recipientName, String phone, String cep, String street, String number,
            String complement, String district, String city, String state, String reference) {
    }

    public record OrderResponse(Long id, Long userId, String status, List<OrderItemResponse> items,
            BigDecimal subtotal, BigDecimal deliveryFee, String deliveryRegion, BigDecimal total,
            BigDecimal refundedAmount, DeliveryAddress deliveryAddress,
            Instant paymentDueAt, Instant paidAt, Instant shippedAt, String trackingUrl, Instant deliveredAt,
            Instant cancelledAt, String cancelReason, String termsVersion, Instant termsAcceptedAt,
            Instant createdAt, Instant updatedAt) {

        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getUserId(), order.getStatus().name(),
                    order.getItems().stream().map(OrderItemResponse::from).toList(),
                    order.getSubtotal(), order.getDeliveryFee(), order.getDeliveryRegion(), order.getTotal(),
                    order.getRefundedAmount(), new DeliveryAddress(order.getRecipientName(), order.getPhone(), order.getCep(),
                            order.getStreet(), order.getNumber(), order.getComplement(), order.getDistrict(),
                            order.getCity(), order.getState(), order.getReference()),
                    order.getPaymentDueAt(), order.getPaidAt(), order.getShippedAt(), order.getTrackingUrl(),
                    order.getDeliveredAt(), order.getCancelledAt(), order.getCancelReason(),
                    order.getTermsVersion(), order.getTermsAcceptedAt(), order.getCreatedAt(), order.getUpdatedAt());
        }
    }

    /** Compact line for order listings. */
    public record OrderSummary(Long id, String status, int itemCount, BigDecimal total, Instant createdAt) {

        public static OrderSummary from(Order order) {
            return new OrderSummary(order.getId(), order.getStatus().name(),
                    order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(), order.getTotal(),
                    order.getCreatedAt());
        }
    }
}
