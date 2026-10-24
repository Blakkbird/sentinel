package com.runtimeerror.sentinel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CSRF protection for a JavaScript single-page app. This is the handler from the Spring
 * Security reference guide ("Single-Page Applications"), unchanged.
 *
 * The attack it stops: you are signed in to Sentinel, then visit a malicious page that
 * quietly submits a form to Sentinel. Your browser attaches your session cookie, so the
 * request would look like you. The defence: every request that changes something must
 * also carry a token in the X-XSRF-TOKEN header. The token sits in a cookie (XSRF-TOKEN)
 * that only pages from our own site can read, so the malicious page cannot copy it.
 *
 * What this class adds: it makes sure the cookie is set on every response, and it accepts
 * the raw token from the header (what our console sends).
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // Masks the token if it is ever rendered into a page (protection against BREACH).
        this.xor.handle(request, response, csrfToken);
        // Loads the token now, which writes the XSRF-TOKEN cookie on this response.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        // A header means our JavaScript sent the raw cookie value; anything else is a masked form field.
        return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
