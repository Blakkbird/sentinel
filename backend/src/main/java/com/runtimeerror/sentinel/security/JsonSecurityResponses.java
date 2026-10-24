package com.runtimeerror.sentinel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Spring Security says back, as JSON.
 *
 * Out of the box it redirects a browser to a login page or sends an HTML error. Neither
 * suits a JavaScript console or a payment system, so every answer here is a small JSON
 * body in the same Problem Details shape the rest of the API uses (title, detail, status).
 * SecurityConfig wires these methods in.
 */
@Component
public class JsonSecurityResponses {

    private final ObjectMapper json;

    public JsonSecurityResponses(ObjectMapper json) {
        this.json = json;
    }

    /** Not signed in at all: 401. */
    void unauthorized(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        boolean decisionApi = request.getRequestURI().startsWith("/v1/");
        problem(response, HttpStatus.UNAUTHORIZED, "Not signed in",
                decisionApi ? "Send a valid API key in the X-Api-Key header." : "Sign in to use the console.");
    }

    /** Signed in, but not allowed to do this, or the CSRF token was missing: 403. */
    void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        if (e instanceof CsrfException) {
            problem(response, HttpStatus.FORBIDDEN, "Request blocked",
                    "The page's security token was missing or out of date. Reload the page and try again.");
        } else {
            problem(response, HttpStatus.FORBIDDEN, "Not allowed", "This account cannot do that.");
        }
    }

    /** Correct username and password: answer with who signed in. The session cookie is already set. */
    void loginSucceeded(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), CurrentUser.of((AppUserPrincipal) authentication.getPrincipal()));
    }

    /** Wrong username or password: 401. Or the user table could not be read: 503. */
    void loginFailed(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        if (e instanceof InternalAuthenticationServiceException) {
            problem(response, HttpStatus.SERVICE_UNAVAILABLE, "Sign-in unavailable",
                    "The user database could not be reached. Try again in a moment.");
        } else {
            problem(response, HttpStatus.UNAUTHORIZED, "Sign-in failed", "Wrong username or password.");
        }
    }

    private void problem(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        json.writeValue(response.getOutputStream(), body);
    }
}
