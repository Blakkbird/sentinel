/**
 * The decision engine: the heart of Sentinel.
 *
 * Every class in this package is plain Java. Nothing here imports Spring, a database
 * driver, Redis, or anything that touches the network. That is a deliberate rule:
 *
 *  - it can be unit tested in milliseconds with no infrastructure running
 *  - it behaves identically in the live API, in replay, and in tests
 *  - it is the code you will explain in interviews, and it reads as ordinary Java
 *
 * Spring lives only at the edges of the application (HTTP, database, Redis) and calls
 * into this package. If you ever find yourself adding a Spring import here, stop and
 * put that code in a different package instead.
 */
package com.runtimeerror.sentinel.engine;
