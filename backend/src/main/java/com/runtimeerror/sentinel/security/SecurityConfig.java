package com.runtimeerror.sentinel.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Who may call what. Two separate rule sets ("filter chains"), because Sentinel has two
 * very different kinds of caller:
 *
 *  1. Payment systems, on /v1. Machines. They send an API key with every request and keep
 *     no session. No cookies are involved, so there is nothing for CSRF to protect.
 *
 *  2. People, on everything else (the console's /api, actuator, Swagger UI). They sign in
 *     once with a username and password; the server remembers them in a session, and the
 *     browser sends the session cookie back automatically. Because the browser sends it
 *     automatically, CSRF protection is switched on here.
 *
 * Spring checks the chains in @Order and uses the first whose matcher fits the request.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BootstrapProperties.class)
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain decisionApi(HttpSecurity http, ApiKeyRegistry keys, JsonSecurityResponses responses)
            throws Exception {
        http
                .securityMatcher("/v1/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/v1/decisions").hasRole("API_CLIENT")
                        .anyRequest().authenticated())
                .addFilterBefore(new ApiKeyAuthFilter(keys), UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(responses::unauthorized)
                        .accessDeniedHandler(responses::forbidden));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain console(HttpSecurity http, JsonSecurityResponses responses) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // The second pass of a streamed (live feed) or failed request was already
                        // checked on its first pass; checking again only produces noise.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Approving, rejecting and changing the live rules are for admins. The
                        // service checks again (and adds maker-checker); this line makes sure an
                        // analyst's request never even reaches it.
                        .requestMatchers(HttpMethod.POST, "/api/rule-sets/*/approve", "/api/rule-sets/*/reject",
                                "/api/rule-sets/*/activate").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .formLogin(form -> form
                        .loginPage("/login")                       // the console's page; Spring generates none
                        .loginProcessingUrl("/api/auth/login")     // the console posts username and password here
                        .successHandler(responses::loginSucceeded)
                        .failureHandler(responses::loginFailed)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(responses::unauthorized)
                        .accessDeniedHandler(responses::forbidden));
        return http.build();
    }

    /** BCrypt: slow on purpose, and salted, so stolen hashes are expensive to crack. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
