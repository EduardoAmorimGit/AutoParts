package com.autoparts_maven.autoparts_maven.returns.dto;

import com.autoparts_maven.autoparts_maven.returns.ReturnRequest;
import com.autoparts_maven.autoparts_maven.returns.ReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record ItemRequest(@NotNull Long orderItemId, @NotNull @Min(1) @Max(99) Integer quantity) {
    }

    /** {@code reason} is optional for a withdrawal (the law requires none) and mandatory for a defect. */
    public record CreateReturnRequest(@NotNull ReturnType type, @Size(max = 1000) String reason,
            @NotEmpty @Size(max = 50) List<@Valid ItemRequest> items) {
    }

    public record NoteRequest(@Size(max = 1000) String note) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record ReceiveRequest(@NotNull Boolean restock, @Size(max = 1000) String note) {
    }

    public record ReturnItemResponse(Long orderItemId, int quantity) {
    }

    public record ReturnResponse(Long id, Long orderId, String type, String status, String customerReason,
            List<ReturnItemResponse> items, BigDecimal itemsAmount, BigDecimal deliveryFeeAmount,
            BigDecimal refundAmount, String adminNote, String rejectionReason, boolean restocked,
            Instant requestedAt, Instant approvedAt, Instant receivedAt, Instant resolvedAt,
            /** Admin view: past the policy deadline to answer (REQUESTED) or to refund/replace (RECEIVED). */
            boolean overdue,
            /** Defects: CDC art. 18 gives the store 30 days to solve them before the customer may demand a refund. */
            Instant legalDeadline) {

        public static ReturnResponse from(ReturnRequest r, boolean overdue, Instant legalDeadline) {
            return new ReturnResponse(r.getId(), r.getOrderId(), r.getType().name(), r.getStatus().name(),
                    r.getCustomerReason(),
                    r.getItems().stream().map(i -> new ReturnItemResponse(i.getOrderItemId(), i.getQuantity())).toList(),
                    r.getItemsAmount(), r.getDeliveryFeeAmount(), r.getRefundAmount(), r.getAdminNote(),
                    r.getRejectionReason(), r.isRestocked(), r.getRequestedAt(), r.getApprovedAt(),
                    r.getReceivedAt(), r.getResolvedAt(), overdue, legalDeadline);
        }
    }
}
