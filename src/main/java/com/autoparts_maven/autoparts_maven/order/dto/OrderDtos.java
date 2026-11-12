package com.autoparts_maven.autoparts_maven.order.dto;

import com.autoparts_maven.autoparts_maven.order.Order;
import com.autoparts_maven.autoparts_maven.order.OrderItem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(@NotNull Long addressId) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    public record OrderItemResponse(Long productId, String sku, String productName, BigDecimal unitPrice,
            int quantity, BigDecimal lineTotal) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getProductId(), item.getSku(), item.getProductName(),
                    item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
        }
    }

    public record DeliveryAddress(String recipientName, String phone, String cep, String street, String number,
            String complement, String district, String city, String state, String reference) {
    }

    public record OrderResponse(Long id, Long userId, String status, List<OrderItemResponse> items,
            BigDecimal subtotal, BigDecimal deliveryFee, String deliveryRegion, BigDecimal total,
            DeliveryAddress deliveryAddress,
            Instant paymentDueAt, Instant cancelledAt, String cancelReason, Instant createdAt, Instant updatedAt) {

        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getUserId(), order.getStatus().name(),
                    order.getItems().stream().map(OrderItemResponse::from).toList(),
                    order.getSubtotal(), order.getDeliveryFee(), order.getDeliveryRegion(), order.getTotal(),
                    new DeliveryAddress(order.getRecipientName(), order.getPhone(), order.getCep(),
                            order.getStreet(), order.getNumber(), order.getComplement(), order.getDistrict(),
                            order.getCity(), order.getState(), order.getReference()),
                    order.getPaymentDueAt(), order.getCancelledAt(), order.getCancelReason(),
                    order.getCreatedAt(), order.getUpdatedAt());
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
