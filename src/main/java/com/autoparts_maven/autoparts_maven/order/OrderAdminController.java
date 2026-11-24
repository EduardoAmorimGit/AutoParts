package com.autoparts_maven.autoparts_maven.order;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderSummary;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.ShipRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only (/api/admin/**) order queries and fulfillment steps: PAID -> PREPARING -> SHIPPED
 * (with the courier tracking link) -> DELIVERED. Cancellation with refund lives in the payment module.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;

    @GetMapping
    public PageResponse<OrderSummary> search(@RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.search(status, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PostMapping("/{id}/prepare")
    public OrderResponse prepare(@PathVariable Long id) {
        return orderService.startPreparing(id);
    }

    @PostMapping("/{id}/ship")
    public OrderResponse ship(@PathVariable Long id, @Valid @RequestBody ShipRequest request) {
        return orderService.markShipped(id, request.trackingUrl());
    }

    @PostMapping("/{id}/deliver")
    public OrderResponse deliver(@PathVariable Long id) {
        return orderService.markDelivered(id);
    }
}
