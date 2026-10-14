package com.runtimeerror.sentinel.features;

/** The counter store could not be reached. The decision continues in degraded mode. */
public class VelocityUnavailableException extends RuntimeException {

    public VelocityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
