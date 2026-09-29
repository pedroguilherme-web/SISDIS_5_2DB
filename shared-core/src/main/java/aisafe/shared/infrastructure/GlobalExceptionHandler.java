package aisafe.shared.infrastructure;

import aisafe.security.domain.InvalidCredentialsException;
import aisafe.shared.domain.ConcurrencyException;
import aisafe.shared.domain.DomainException;
import aisafe.shared.domain.DuplicateResourceException;
import aisafe.shared.domain.ResourceInUseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler that translates shared domain and infrastructure exceptions into
 * meaningful HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({
            DomainException.class,
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, DuplicateResourceException.class, ResourceInUseException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex) {
        String msg = (ex instanceof DuplicateResourceException || ex instanceof ResourceInUseException)
                ? ex.getMessage()
                : "A resource with the given unique identifier already exists.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("You do not have permission to access this resource."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("The resource was modified by another request. Please retry."));
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<ErrorResponse> handlePreconditionFailed(ConcurrencyException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ValidationErrorResponse("Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected internal server error occurred: " + ex.getMessage()));
    }

    public record ErrorResponse(String message) {}
    public record ValidationErrorResponse(String message, Map<String, String> errors) {}
}
