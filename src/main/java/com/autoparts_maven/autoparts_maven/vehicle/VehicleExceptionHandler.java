package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupAuthenticationException;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupProviderException;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupRateLimitedException;
import com.autoparts_maven.autoparts_maven.integration.vehicle.VehicleLookupUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class VehicleExceptionHandler {

    @ExceptionHandler(VehicleNotFoundException.class)
    ProblemDetail handleVehicleNotFound(VehicleNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Vehicle not found");
        return problem;
    }

    @ExceptionHandler(InvalidPlateException.class)
    ProblemDetail handleInvalidPlate(InvalidPlateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid plate");
        return problem;
    }

    @ExceptionHandler(VehicleLookupDisabledException.class)
    ProblemDetail handleLookupDisabled(VehicleLookupDisabledException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Vehicle plate lookup is not configured");
        problem.setTitle("Lookup unavailable");
        return problem;
    }

    @ExceptionHandler(VehicleLookupUnavailableException.class)
    ProblemDetail handleLookupUnavailable(VehicleLookupUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Vehicle lookup provider is temporarily unavailable");
        problem.setTitle("Lookup unavailable");
        return problem;
    }

    @ExceptionHandler(VehicleLookupRateLimitedException.class)
    ProblemDetail handleRateLimited(VehicleLookupRateLimitedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Vehicle lookup rate limit exceeded");
        problem.setTitle("Rate limited");
        return problem;
    }

    @ExceptionHandler(VehicleLookupAuthenticationException.class)
    ProblemDetail handleLookupAuthentication(VehicleLookupAuthenticationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Vehicle lookup provider rejected the credentials");
        problem.setTitle("Upstream authentication failed");
        return problem;
    }

    @ExceptionHandler(VehicleLookupProviderException.class)
    ProblemDetail handleProviderError(VehicleLookupProviderException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Vehicle lookup provider returned an unexpected response");
        problem.setTitle("Upstream error");
        return problem;
    }
}
