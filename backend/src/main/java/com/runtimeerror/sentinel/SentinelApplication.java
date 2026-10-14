package com.runtimeerror.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Running main() starts an embedded web server on port 8080, connects to
 * Postgres and Redis, applies database migrations, and creates every object the app
 * needs.
 */
@SpringBootApplication
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
    }
}
