package com.poker.exception;

import com.poker.dto.ApiError;
import com.poker.dto.ApiFieldError;
import com.poker.game.engine.GameActionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("API error {} {} on {}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(GameActionException.class)
    public ResponseEntity<ApiError> handleGameAction(GameActionException ex, HttpServletRequest request) {
        log.warn("Engine error {} {} on {}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        HttpStatus status = switch (ex.getCode()) {
            case ErrorCodes.SEAT_UNAVAILABLE, ErrorCodes.GAME_ALREADY_STARTED -> HttpStatus.CONFLICT;
            case ErrorCodes.PLAYER_NOT_FOUND, ErrorCodes.GAME_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return respond(status, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "Request body is invalid or missing.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'.",
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "The requested resource was not found.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request
        );
    }

    private ResponseEntity<ApiError> respond(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return respond(status, code, message, request, List.of());
    }

    private ResponseEntity<ApiError> respond(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> errors
    ) {
        ApiError body = ApiError.of(status.value(), code, message, request.getRequestURI(), errors);
        return ResponseEntity.status(status).body(body);
    }
}
