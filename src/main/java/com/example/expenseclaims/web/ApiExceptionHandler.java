package com.example.expenseclaims.web;

import com.example.expenseclaims.security.AuthenticationRequiredException;
import com.example.expenseclaims.service.ClaimNotFoundException;
import com.example.expenseclaims.service.ClaimSubmissionNotAllowedException;
import com.example.expenseclaims.service.EmployeeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into JSON error responses in the standard "problem details" format (RFC 9457):
 * {"type", "title", "status", "detail", "instance"}, with Content-Type application/problem+json.
 *
 * Extending ResponseEntityExceptionHandler lets Spring handle its own errors the same way:
 * malformed JSON, an unknown category value or a non-numeric id each become a 400, and an
 * unknown URL becomes a 404. Responses never contain stack traces, exception class names or SQL.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // A request body that fails @Valid: one entry per invalid field, for example {"amount": "..."}.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // A field can break two rules at once; keep both messages.
            errors.merge(fieldError.getField(), fieldError.getDefaultMessage(), (first, second) -> first + "; " + second);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body contains invalid fields.");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({ClaimNotFoundException.class, EmployeeNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ProblemDetail handleAuthenticationRequired(AuthenticationRequiredException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", ex.getMessage());
    }

    @ExceptionHandler(ClaimSubmissionNotAllowedException.class)
    public ProblemDetail handleSubmissionNotAllowed(ClaimSubmissionNotAllowedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Not allowed", ex.getMessage());
    }

    // Anything unexpected (for example a database outage): log the details on the server,
    // but tell the client only that something went wrong.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling a request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
