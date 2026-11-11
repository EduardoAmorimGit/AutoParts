package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.DuplicateSpecException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.FitmentNotFoundException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.InvalidImportFileException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.InvalidSpecException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.SpecNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
class CompatibilityExceptionHandler {

    @ExceptionHandler({SpecNotFoundException.class, FitmentNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception);
    }

    @ExceptionHandler(DuplicateSpecException.class)
    ProblemDetail handleDuplicate(DuplicateSpecException exception) {
        return problem(HttpStatus.CONFLICT, "Duplicate vehicle spec", exception);
    }

    @ExceptionHandler({InvalidImportFileException.class, InvalidSpecException.class})
    ProblemDetail handleInvalid(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
