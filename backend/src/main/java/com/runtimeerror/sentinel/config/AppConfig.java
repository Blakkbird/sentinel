package com.runtimeerror.sentinel.config;

import com.runtimeerror.sentinel.engine.DecisionEngine;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Objects that Spring cannot discover by itself, declared by hand.
 *
 * A method marked @Bean is a factory: Spring calls it once at startup and hands the
 * result to any class that asks for that type in its constructor.
 */
@Configuration
public class AppConfig {

    /**
     * The current time, provided as an object rather than read with Instant.now().
     * Production uses the real UTC clock. Tests swap in a fixed clock, which makes
     * time-dependent behaviour (like one-minute windows) exactly reproducible.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The engine package deliberately has no Spring annotations (see its package-info),
     * so it is registered here instead of with @Component.
     */
    @Bean
    public DecisionEngine decisionEngine() {
        return new DecisionEngine();
    }

    /**
     * Makes the Redis client fail immediately while it knows Redis is disconnected,
     * instead of queueing each command until its timeout expires. With this, a dead
     * Redis costs a decision almost nothing; without it, every decision would wait out
     * the full timeout before falling back to degraded mode.
     *
     * Stage 2 adds a circuit breaker, which also covers the case where Redis was never
     * reachable in the first place.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown(RedisProperties redis) {
        Duration connectTimeout = redis.getConnectTimeout() != null
                ? redis.getConnectTimeout()
                : Duration.ofMillis(200);
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }
}
