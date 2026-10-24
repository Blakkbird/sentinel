package com.runtimeerror.sentinel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Signs in callers of /v1 by the X-Api-Key header.
 *
 * A filter runs before any controller. This one only answers "who is this?": if the key
 * is valid it records the caller as authenticated, with the role API_CLIENT. Whether that
 * caller may do what it is asking is decided afterwards, by the rules in SecurityConfig.
 * A missing or wrong key simply leaves the request anonymous, and those rules turn it away
 * with a 401.
 *
 * Deliberately not a @Component. Spring Boot registers every Filter bean for every
 * request, which would run this on the console's requests too. SecurityConfig creates it
 * and puts it in the /v1 chain only.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRegistry keys;

    public ApiKeyAuthFilter(ApiKeyRegistry keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(ApiKeys.HEADER);
        if (presented != null && !presented.isBlank()) {
            keys.find(presented.strip()).ifPresent(client -> {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        client, null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
