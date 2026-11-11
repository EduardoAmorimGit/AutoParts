package com.autoparts_maven.autoparts_maven.identity;

import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.EmailAlreadyRegisteredException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.InvalidCredentialsException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.InvalidRefreshTokenException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.UserNotFoundException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.WrongCurrentPasswordException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
class IdentityExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException exception) {
        return problem(HttpStatus.CONFLICT, "E-mail already registered", exception);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", exception);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefresh(InvalidRefreshTokenException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid refresh token", exception);
    }

    @ExceptionHandler(WrongCurrentPasswordException.class)
    ProblemDetail handleWrongPassword(WrongCurrentPasswordException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "User not found", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
