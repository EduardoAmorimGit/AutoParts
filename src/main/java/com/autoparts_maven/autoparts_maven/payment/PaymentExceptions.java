package com.autoparts_maven.autoparts_maven.payment;

import com.autoparts_maven.autoparts_maven.order.OrderStatus;
import com.autoparts_maven.autoparts_maven.payment.gateway.PaymentGatewayException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class PaymentNotFoundException extends RuntimeException {
    PaymentNotFoundException(Long id) {
        super("Payment with id " + id + " not found");
    }
}

class OrderNotPayableException extends RuntimeException {
    OrderNotPayableException(Long orderId, OrderStatus status) {
        super("Order " + orderId + " cannot be paid in status " + status);
    }
}

class PaymentInProgressException extends RuntimeException {
    PaymentInProgressException() {
        super("A payment for this order is already being processed; try again in a moment");
    }
}

class PaymentsDisabledException extends RuntimeException {
    PaymentsDisabledException() {
        super("Payments are not configured");
    }
}

class InvalidPaymentRequestException extends RuntimeException {
    InvalidPaymentRequestException(String message) {
        super(message);
    }
}

class InvalidWebhookSignatureException extends RuntimeException {
    InvalidWebhookSignatureException() {
        super("Invalid notification signature");
    }
}

@RestControllerAdvice
@Order(0)
class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail handleNotFound(PaymentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", exception.getMessage());
    }

    @ExceptionHandler({OrderNotPayableException.class, PaymentInProgressException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    ProblemDetail handleInvalid(InvalidPaymentRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    ProblemDetail handleSignature(InvalidWebhookSignatureException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage());
    }

    @ExceptionHandler(PaymentsDisabledException.class)
    ProblemDetail handleDisabled(PaymentsDisabledException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payments unavailable", exception.getMessage());
    }

    // Provider details are never exposed to clients; logs keep only the HTTP status.
    @ExceptionHandler(PaymentGatewayException.class)
    ProblemDetail handleGateway(PaymentGatewayException exception) {
        return exception.isUnavailable()
                ? problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider unavailable",
                        "The payment provider is unavailable; try again in a few minutes")
                : problem(HttpStatus.BAD_GATEWAY, "Payment refused",
                        "The payment provider refused the request; check the payment data and try again");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
