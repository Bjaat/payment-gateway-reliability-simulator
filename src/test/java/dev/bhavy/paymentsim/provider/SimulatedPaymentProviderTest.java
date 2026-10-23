package dev.bhavy.paymentsim.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bhavy.paymentsim.domain.AttemptOutcome;
import org.junit.jupiter.api.Test;

class SimulatedPaymentProviderTest {

    private final SimulatedPaymentProvider provider = new SimulatedPaymentProvider();

    @Test
    void defaultTokenSucceeds() {
        assertThat(provider.charge("tok_success", 100, 1).outcome()).isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void declineTokenIsPermanentOnEveryAttempt() {
        assertThat(provider.charge("tok_decline", 100, 1).outcome()).isEqualTo(AttemptOutcome.PERMANENT_DECLINE);
        assertThat(provider.charge("tok_decline", 100, 3).outcome()).isEqualTo(AttemptOutcome.PERMANENT_DECLINE);
    }

    @Test
    void flakyOnceFailsOnlyTheFirstAttempt() {
        assertThat(provider.charge("tok_flaky_once", 100, 1).outcome()).isEqualTo(AttemptOutcome.TRANSIENT_FAILURE);
        assertThat(provider.charge("tok_flaky_once", 100, 2).outcome()).isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void flakyTwiceFailsFirstTwoAttempts() {
        assertThat(provider.charge("tok_flaky_twice", 100, 2).outcome()).isEqualTo(AttemptOutcome.TRANSIENT_FAILURE);
        assertThat(provider.charge("tok_flaky_twice", 100, 3).outcome()).isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void timeoutTokenAlwaysFailsTransiently() {
        assertThat(provider.charge("tok_timeout", 100, 5).outcome()).isEqualTo(AttemptOutcome.TRANSIENT_FAILURE);
    }
}
