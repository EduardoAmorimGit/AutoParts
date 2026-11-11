package com.autoparts_maven.autoparts_maven.cart;

import com.autoparts_maven.autoparts_maven.cart.dto.CartDtos.CartResponse;
import com.autoparts_maven.autoparts_maven.cart.dto.CartDtos.SetQuantityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The signed-in customer's cart (any authenticated user). */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return cartService.get(userId(jwt));
    }

    @PutMapping("/items/{productId}")
    public CartResponse setQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId,
            @Valid @RequestBody SetQuantityRequest request) {
        return cartService.setQuantity(userId(jwt), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        return cartService.remove(userId(jwt), productId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt) {
        cartService.clear(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

class CartFullException extends RuntimeException {
    CartFullException(int max) {
        super("The cart can hold at most " + max + " different products");
    }
}

@RestControllerAdvice
@Order(0)
class CartExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail handleStock(InsufficientStockException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Insufficient stock");
        problem.setProperty("sku", exception.getSku());
        problem.setProperty("available", exception.getAvailable());
        return problem;
    }

    @ExceptionHandler(CartFullException.class)
    ProblemDetail handleFull(CartFullException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }
}
