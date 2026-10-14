package com.runtimeerror.sentinel.api;

/** No decision exists for the transaction id that was asked about. Becomes a 404. */
public class DecisionNotFoundException extends RuntimeException {

    public DecisionNotFoundException(String transactionId) {
        super("No decision has been recorded for transaction " + transactionId);
    }
}
