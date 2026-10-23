package dev.bhavy.paymentsim.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentStatusTest {

    @Test
    void pendingMayOnlyMoveToProcessing() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void processingMayReachAnyTerminalState() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.DECLINED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCEEDED", "DECLINED", "FAILED"})
    void terminalStatesCannotBeLeft(PaymentStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (PaymentStatus next : PaymentStatus.values()) {
            assertThat(terminal.canTransitionTo(next)).isFalse();
        }
    }

    @Test
    void paymentRejectsIllegalTransition() {
        Payment payment = new Payment("key-1", "hash", "merchant_1", 1000, "INR", "tok_success");

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.SUCCEEDED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.transitionTo(PaymentStatus.SUCCEEDED);

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.FAILED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
