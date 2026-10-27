package com.runtimeerror.sentinel.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The error handler on its own, with no application context, database or Docker.
 *
 * "Standalone" MockMvc builds just enough of Spring MVC to call one controller, here a tiny
 * fake whose only job is to throw the exceptions we want to see handled. So this test runs
 * on any laptop in well under a second, unlike the integration tests.
 */
class ApiExceptionHandlerTest {

    @RestController
    public static class Broken {

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/test/outage")
        public void outage() {
            throw new DataAccessResourceFailureException("connection refused");
        }

        @PostMapping("/api/test/echo")
        public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
            return body;
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Broken())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void aBrokenConstraintIsAConflictNotAnOutage() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void anUnreachableDatabaseIsStillAnOutage() throws Exception {
        mvc.perform(get("/test/outage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Database unavailable"));
    }

    @Test
    void unreadableJsonOutsideThePaymentApiGetsNoDateHint() throws Exception {
        mvc.perform(post("/api/test/echo").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unreadable request"))
                .andExpect(jsonPath("$.detail").value(not(containsString("occurredAt"))));
    }
}
