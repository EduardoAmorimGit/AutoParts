package com.autoparts_maven.autoparts_maven.returns;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ReturnNotFoundException extends RuntimeException {
    ReturnNotFoundException(Long id) {
        super("Return request " + id + " not found");
    }
}

class InvalidReturnTransitionException extends RuntimeException {
    InvalidReturnTransitionException(Long id, ReturnStatus from, ReturnStatus to) {
        super("Return request " + id + " cannot go from " + from + " to " + to);
    }
}

/** The request breaks a rule the customer can fix or must be told about (422). */
class ReturnNotAllowedException extends RuntimeException {
    ReturnNotAllowedException(String message) {
        super(message);
    }
}

@RestControllerAdvice
@Order(0)
class ReturnExceptionHandler {

    @ExceptionHandler(ReturnNotFoundException.class)
    ProblemDetail handleNotFound(ReturnNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Return request not found", exception.getMessage());
    }

    @ExceptionHandler(InvalidReturnTransitionException.class)
    ProblemDetail handleTransition(InvalidReturnTransitionException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(ReturnNotAllowedException.class)
    ProblemDetail handleNotAllowed(ReturnNotAllowedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Return not allowed", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
