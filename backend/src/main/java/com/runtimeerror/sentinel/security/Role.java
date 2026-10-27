package com.runtimeerror.sentinel.security;

/** What a person signed in to the console is allowed to do. */
public enum Role {

    /** Reads everything, drafts rule changes and sends them for approval. */
    ANALYST,

    /**
     * Everything an analyst can do, plus approving (someone else's) rule changes, rejecting them,
     * rolling back, and the admin endpoints.
     */
    ADMIN
}
