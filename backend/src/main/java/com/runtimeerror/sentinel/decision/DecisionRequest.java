package com.runtimeerror.sentinel.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * A payment attempt that needs a decision. This is the JSON a caller sends.
 *
 * The annotations are validation rules. Spring checks them before any of our code runs,
 * and a request that breaks one gets a 400 response listing every bad field.
 *
 * Money is a whole number of paise in a long, never a double. Binary floating point
 * cannot represent most decimal fractions exactly (0.1 + 0.2 is not 0.3), and small
 * errors in a payment system add up into real money.
 */
public record DecisionRequest(
        @NotBlank @Size(max = 64) String transactionId,
        @NotBlank @Size(max = 64) String cardId,
        @Size(max = 64) String deviceId,
        @Size(max = 64) String merchantId,
        @NotNull @Positive Long amountMinor,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a three letter code such as INR") String currency,
        @NotNull Instant occurredAt
) {
}
