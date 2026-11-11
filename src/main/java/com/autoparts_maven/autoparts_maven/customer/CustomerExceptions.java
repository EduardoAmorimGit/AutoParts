package com.autoparts_maven.autoparts_maven.customer;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class AddressNotFoundException extends RuntimeException {
    AddressNotFoundException(Long id) {
        super("Address with id " + id + " not found");
    }
}

class AddressLimitException extends RuntimeException {
    AddressLimitException(int max) {
        super("A customer can keep at most " + max + " addresses");
    }
}

class InvalidAddressException extends RuntimeException {
    InvalidAddressException(String message) {
        super(message);
    }
}

@RestControllerAdvice
@Order(0)
class CustomerExceptionHandler {

    @ExceptionHandler(AddressNotFoundException.class)
    ProblemDetail handleNotFound(AddressNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Address not found", exception);
    }

    @ExceptionHandler(OutsideDeliveryAreaException.class)
    ProblemDetail handleOutsideArea(OutsideDeliveryAreaException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Outside delivery area", exception);
    }

    @ExceptionHandler({AddressLimitException.class, InvalidAddressException.class})
    ProblemDetail handleInvalid(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
