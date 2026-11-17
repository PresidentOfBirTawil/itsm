package com.itsm.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.itsm.error.DuplicateIncidentException;
import com.itsm.error.InvalidInputException;
import com.itsm.error.ItsmException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errors of the API controllers as "problem details" (RFC 9457) JSON with
 * the right status code, instead of the web app's error pages:
 * 400 a request that breaks a rule, 403 not allowed, 404 not found,
 * 409 not possible in the incident's current state, 500 our fault.
 */
@RestControllerAdvice(basePackages = "com.itsm.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ProblemDetail> forbidden(UnauthorizedOperationException e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ProblemDetail> invalid(InvalidInputException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage(), request);
    }

    @ExceptionHandler({DuplicateIncidentException.class, ItsmException.class})
    public ResponseEntity<ProblemDetail> conflict(ItsmException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Not possible now", e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> fields(MethodArgumentNotValidException e, HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Some fields are missing or wrong.", request);
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is not valid" : error.getDefaultMessage()))
                .toList();
        response.getBody().setProperty("errors", errors);
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> unreadable(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The request could not be read: check that it is valid JSON and that every value has the right type.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        log.error("API request {} {} failed", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error",
                "Something went wrong on our side. Nothing was changed. Please try again.", request);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                         HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}
