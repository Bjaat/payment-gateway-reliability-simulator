package dev.bhavy.paymentsim.web;

import dev.bhavy.paymentsim.domain.Payment;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String status,
        String merchantId,
        long amountMinor,
        String currency,
        String failureCode,
        long attemptCount,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment p, long attemptCount) {
        return new PaymentResponse(p.getId(), p.getStatus().name(), p.getMerchantId(), p.getAmountMinor(),
                p.getCurrency(), p.getFailureCode(), attemptCount, p.getCreatedAt(), p.getUpdatedAt());
    }
}
