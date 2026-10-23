package dev.bhavy.paymentsim.domain;

/**
 * Explicit payment lifecycle. Terminal states cannot be left.
 *
 * PENDING -> PROCESSING -> SUCCEEDED | DECLINED | FAILED
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    DECLINED,
    FAILED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == PROCESSING;
            case PROCESSING -> next == SUCCEEDED || next == DECLINED || next == FAILED;
            case SUCCEEDED, DECLINED, FAILED -> false;
        };
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == DECLINED || this == FAILED;
    }
}
