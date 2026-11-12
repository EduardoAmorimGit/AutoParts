package com.autoparts_maven.autoparts_maven.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class OrderNotFoundException extends RuntimeException {
    OrderNotFoundException(Long id) {
        super("Order with id " + id + " not found");
    }
}

class EmptyCartException extends RuntimeException {
    EmptyCartException() {
        super("The cart is empty");
    }
}

class ProductUnavailableException extends RuntimeException {
    ProductUnavailableException(String sku) {
        super("Product '" + sku + "' is no longer available; remove it from the cart");
    }
}

class InvalidIdempotencyKeyException extends RuntimeException {
    InvalidIdempotencyKeyException() {
        super("Header Idempotency-Key is required: 8 to 64 letters, digits, '-' or '_'");
    }
}

class InvalidOrderTransitionException extends RuntimeException {
    InvalidOrderTransitionException(Long id, OrderStatus from, OrderStatus to) {
        super("Order " + id + " cannot go from " + from + " to " + to);
    }
}

@RestControllerAdvice
@org.springframework.core.annotation.Order(0)
class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", exception);
    }

    @ExceptionHandler({EmptyCartException.class, InvalidIdempotencyKeyException.class})
    ProblemDetail handleBadRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception);
    }

    @ExceptionHandler({ProductUnavailableException.class, InvalidOrderTransitionException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
