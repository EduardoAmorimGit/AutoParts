package com.autoparts_maven.autoparts_maven.order;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.CancelRequest;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.CheckoutRequest;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderSummary;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

/** Customer checkout and own orders (any authenticated user). */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates an order from the cart. The client generates one Idempotency-Key per checkout
     * attempt (e.g. a UUID) and reuses it on retries, so a double click never creates two orders.
     */
    @PostMapping("/api/checkout")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        OrderResponse order = orderService.checkout(userId(jwt), request.addressId(), idempotencyKey);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping("/api/orders")
    public PageResponse<OrderSummary> listMine(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listMine(userId(jwt), pageable);
    }

    @GetMapping("/api/orders/{id}")
    public OrderResponse getMine(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return orderService.getMine(userId(jwt), id);
    }

    @PostMapping("/api/orders/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelRequest request) {
        return orderService.cancelMine(userId(jwt), id, request == null ? null : request.reason());
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
