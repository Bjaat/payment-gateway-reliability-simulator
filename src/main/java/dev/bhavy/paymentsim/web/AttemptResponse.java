package dev.bhavy.paymentsim.web;

import dev.bhavy.paymentsim.domain.PaymentAttempt;
import java.time.Instant;

public record AttemptResponse(int attemptNumber, String outcome, String errorCode, Instant createdAt) {

    static AttemptResponse from(PaymentAttempt a) {
        return new AttemptResponse(a.getAttemptNumber(), a.getOutcome().name(), a.getErrorCode(), a.getCreatedAt());
    }
}
