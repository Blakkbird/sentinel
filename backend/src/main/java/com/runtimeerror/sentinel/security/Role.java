package com.runtimeerror.sentinel.security;

/** What a person signed in to the console is allowed to do. */
public enum Role {

    /** Reads everything. From Sprint 2, also drafts rule changes. */
    ANALYST,

    /** Everything an analyst can do, plus approving and publishing rules and the admin endpoints. */
    ADMIN
}
