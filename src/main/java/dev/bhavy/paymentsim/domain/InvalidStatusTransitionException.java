package dev.bhavy.paymentsim.domain;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Illegal payment status transition: " + from + " -> " + to);
    }
}
