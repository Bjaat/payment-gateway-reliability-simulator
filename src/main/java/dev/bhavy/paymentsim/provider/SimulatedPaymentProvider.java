package dev.bhavy.paymentsim.provider;

import org.springframework.stereotype.Component;

/**
 * Deterministic provider simulation driven by the synthetic token, so behaviour is repeatable in tests.
 *
 * <ul>
 *   <li>{@code tok_decline}     - permanent decline on every attempt</li>
 *   <li>{@code tok_flaky_once}  - transient failure on attempt 1, then success</li>
 *   <li>{@code tok_flaky_twice} - transient failure on attempts 1-2, then success</li>
 *   <li>{@code tok_timeout}     - transient failure on every attempt</li>
 *   <li>any other valid token   - success</li>
 * </ul>
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResult charge(String paymentMethodToken, long amountMinor, int attemptNumber) {
        return switch (paymentMethodToken) {
            case "tok_decline" -> ProviderResult.permanentDecline("DECLINED_BY_ISSUER_SIM");
            case "tok_flaky_once" -> attemptNumber <= 1
                    ? ProviderResult.transientFailure("PROVIDER_UNAVAILABLE_SIM")
                    : ProviderResult.success();
            case "tok_flaky_twice" -> attemptNumber <= 2
                    ? ProviderResult.transientFailure("PROVIDER_UNAVAILABLE_SIM")
                    : ProviderResult.success();
            case "tok_timeout" -> ProviderResult.transientFailure("PROVIDER_TIMEOUT_SIM");
            default -> ProviderResult.success();
        };
    }
}
