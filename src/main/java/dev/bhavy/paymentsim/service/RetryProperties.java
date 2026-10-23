package dev.bhavy.paymentsim.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments.retry")
public record RetryProperties(int maxAttempts, long backoffMs) {

    public RetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("payments.retry.max-attempts must be >= 1");
        }
        if (backoffMs < 0) {
            throw new IllegalArgumentException("payments.retry.backoff-ms must be >= 0");
        }
    }
}
