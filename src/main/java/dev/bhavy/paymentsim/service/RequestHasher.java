package dev.bhavy.paymentsim.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fingerprint of the business payload, used to detect Idempotency-Key reuse with a different request. */
final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(String merchantId, long amountMinor, String currency, String paymentMethodToken) {
        String canonical = String.join("|",
                merchantId.trim(),
                Long.toString(amountMinor),
                currency.trim().toUpperCase(),
                paymentMethodToken.trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
