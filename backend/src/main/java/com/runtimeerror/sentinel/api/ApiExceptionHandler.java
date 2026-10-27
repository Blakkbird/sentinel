package com.runtimeerror.sentinel.api;

import com.runtimeerror.sentinel.engine.InvalidRuleSetException;
import com.runtimeerror.sentinel.rules.RuleSetNotFoundException;
import com.runtimeerror.sentinel.rules.RuleSetRefusedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into consistent error responses for the whole API.
 *
 * Errors use the "Problem Details" format, an internet standard (RFC 9457) for JSON
 * error bodies, so every error raised here has the same shape: status, title, detail,
 * plus extra fields where useful. A caller (or the console) writes one piece of error
 * handling code and it works for every endpoint.
 *
 * (Spring Boot has a switch, spring.mvc.problemdetails.enabled, that does this for
 * framework errors too. We leave it off: its handler takes priority over this class for
 * validation errors (it is registered with @Order(0)) and would drop our "fields" map.)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** A field broke a validation rule: 400, listing every bad field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        Map<String, String> fields = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "One or more fields are invalid.");
        problem.setTitle("Invalid request");
        problem.setProperty("fields", fields);
        return problem;
    }

    /** The body was not readable JSON, or a value had the wrong format: 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableRequest(HttpMessageNotReadableException e, HttpServletRequest request) {
        // The date hint only makes sense for the payment API; the console's endpoints have no occurredAt.
        String hint = request.getRequestURI().startsWith("/v1/decisions")
                ? " (occurredAt must look like 2026-09-27T10:15:30Z)."
                : ".";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is not valid JSON, or a value has the wrong format" + hint);
        problem.setTitle("Unreadable request");
        return problem;
    }

    /** A query parameter had the wrong type, for example outcome=MAYBE: 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidParameter(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "'" + e.getValue() + "' is not a valid value for " + e.getName() + ".");
        problem.setTitle("Invalid parameter");
        return problem;
    }

    /** Any other request we cannot act on, with a message saying why: 400. */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    /** GET for a transaction we have never decided: 404. */
    @ExceptionHandler(DecisionNotFoundException.class)
    public ProblemDetail decisionNotFound(DecisionNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Decision not found");
        return problem;
    }

    /** A rule set version that does not exist: 404. */
    @ExceptionHandler(RuleSetNotFoundException.class)
    public ProblemDetail ruleSetNotFound(RuleSetNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Rule set not found");
        return problem;
    }

    /**
     * A rule set that does not hold together: 400, listing every problem, each with its place,
     * for example "rules[2] (VEL_CARD_1M).condition.op: must be one of GT, GTE, ...". The rule
     * builder shows them next to the rules they are about.
     */
    @ExceptionHandler(InvalidRuleSetException.class)
    public ProblemDetail invalidRuleSet(InvalidRuleSetException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e.problems().size() == 1 ? "The rule set has a problem." : "The rule set has " + e.problems().size() + " problems.");
        problem.setTitle("Rule set is invalid");
        problem.setProperty("problems", e.problems());
        return problem;
    }

    /**
     * A rule workflow step that was refused, with the reason. 403 when this person may never do
     * it (only the author edits a draft), 409 when nobody can right now (it is no longer a draft).
     */
    @ExceptionHandler(RuleSetRefusedException.class)
    public ProblemDetail ruleSetRefused(RuleSetRefusedException e) {
        HttpStatus status = e.forbidden() ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(e.forbidden() ? "Not allowed" : "Not possible right now");
        return problem;
    }

    /**
     * The database refused a change because it broke a constraint, for example a unique
     * column: 409. The database is working fine, so this must not be reported as an outage.
     *
     * Spring picks the most specific handler for an exception. DataIntegrityViolationException
     * is a kind of DataAccessException, so it lands here rather than in the 503 handler below.
     * (The decision path catches its own DuplicateKeyException in DecisionService, so a
     * retried payment never reaches this.)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail constraintViolated(DataIntegrityViolationException e) {
        log.warn("constraint violated: {}", e.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The change conflicts with data that is already stored. Reload and try again.");
        problem.setTitle("Conflict");
        return problem;
    }

    /**
     * The database failed: 503, and no decision was made.
     *
     * Redis failures never reach this handler. The velocity store catches them and the
     * decision carries on in degraded mode. Only the decision log is allowed to stop a
     * decision, because answering without a record is the one thing Sentinel never does.
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail decisionLogUnavailable(DataAccessException e, HttpServletRequest request) {
        log.error("database unavailable", e);
        if ("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/v1/decisions")) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "The decision could not be recorded, so no decision was made."
                            + " Retry with the same transactionId.");
            problem.setTitle("Decision log unavailable");
            return problem;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The database could not be reached. Try again in a moment.");
        problem.setTitle("Database unavailable");
        return problem;
    }
}
