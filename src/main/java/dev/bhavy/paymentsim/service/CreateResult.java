package dev.bhavy.paymentsim.service;

import dev.bhavy.paymentsim.domain.Payment;

/** @param replayed true when an existing payment was returned for a repeated Idempotency-Key */
public record CreateResult(Payment payment, boolean replayed) {
}
