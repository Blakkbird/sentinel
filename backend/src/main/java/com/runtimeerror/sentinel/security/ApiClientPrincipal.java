package com.runtimeerror.sentinel.security;

import java.security.Principal;

/** Who is calling the decision API: the client that owns the key that was sent. */
public record ApiClientPrincipal(String name, String keyPrefix) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
