package dev.bhavy.paymentsim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false, length = 36)
    private String paymentId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private AttemptOutcome outcome;

    @Column(name = "error_code", updatable = false, length = 64)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAttempt() {
        // for JPA
    }

    public PaymentAttempt(String paymentId, int attemptNumber, AttemptOutcome outcome, String errorCode) {
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public int getAttemptNumber() { return attemptNumber; }
    public AttemptOutcome getOutcome() { return outcome; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
}
