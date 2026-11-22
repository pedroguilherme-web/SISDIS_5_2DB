package aisafe.flights.infrastructure;

import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.RemoteRequestRejectedException;
import aisafe.flights.domain.InvalidFlightStatusTransitionException;
import aisafe.shared.infrastructure.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlightApiExceptionHandler {
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(ExternalServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RemoteRequestRejectedException.class)
    public ResponseEntity<ErrorResponse> handleRejected(RemoteRequestRejectedException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        return ResponseEntity.status(status != null ? status : HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidFlightStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidFlightStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
}
