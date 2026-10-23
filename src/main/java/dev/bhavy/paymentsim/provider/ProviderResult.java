package dev.bhavy.paymentsim.provider;

import dev.bhavy.paymentsim.domain.AttemptOutcome;

public record ProviderResult(AttemptOutcome outcome, String errorCode) {

    public static ProviderResult success() {
        return new ProviderResult(AttemptOutcome.SUCCESS, null);
    }

    public static ProviderResult transientFailure(String code) {
        return new ProviderResult(AttemptOutcome.TRANSIENT_FAILURE, code);
    }

    public static ProviderResult permanentDecline(String code) {
        return new ProviderResult(AttemptOutcome.PERMANENT_DECLINE, code);
    }
}
