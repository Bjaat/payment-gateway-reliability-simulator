package dev.bhavy.paymentsim.provider;

/** Boundary to an external payment provider. Only a simulator implements it in this project. */
public interface PaymentProvider {

    /**
     * @param paymentMethodToken opaque synthetic token (never a card number)
     * @param amountMinor        amount in minor units
     * @param attemptNumber      1-based attempt counter for this payment
     */
    ProviderResult charge(String paymentMethodToken, long amountMinor, int attemptNumber);
}
