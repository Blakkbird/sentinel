package com.runtimeerror.sentinel.api;

/** A request we cannot act on, with a message that says why. Becomes a 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
