package com.autoparts_maven.autoparts_maven.delivery;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class DeliveryRegionNotFoundException extends RuntimeException {
    DeliveryRegionNotFoundException(Long id) {
        super("Delivery region with id " + id + " not found");
    }
}

class DuplicateRegionNameException extends RuntimeException {
    DuplicateRegionNameException(String name) {
        super("A delivery region named '" + name + "' already exists");
    }
}

class DistrictAlreadyAssignedException extends RuntimeException {

    private final transient List<DistrictOwner> owners;

    DistrictAlreadyAssignedException(List<DistrictOwner> owners) {
        super("Some districts already belong to another region; remove them there first");
        this.owners = List.copyOf(owners);
    }

    List<DistrictOwner> getOwners() {
        return owners;
    }
}

/** A concurrent edit claimed the same name or district first (caught by the unique indexes). */
class ConcurrentRegionEditException extends RuntimeException {
    ConcurrentRegionEditException(DataIntegrityViolationException cause) {
        super("The region name or a district was taken by another edit; reload and try again", cause);
    }
}

@RestControllerAdvice
@Order(0)
class DeliveryExceptionHandler {

    @ExceptionHandler(DeliveryRegionNotFoundException.class)
    ProblemDetail handleNotFound(DeliveryRegionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Delivery region not found", exception);
    }

    @ExceptionHandler({DuplicateRegionNameException.class, ConcurrentRegionEditException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception);
    }

    @ExceptionHandler(DistrictAlreadyAssignedException.class)
    ProblemDetail handleAssigned(DistrictAlreadyAssignedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "District already assigned", exception);
        problem.setProperty("districts", exception.getOwners());
        return problem;
    }

    @ExceptionHandler(DistrictNotServedException.class)
    ProblemDetail handleNotServed(DistrictNotServedException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "District not served", exception);
        problem.setProperty("district", exception.getDistrict());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
