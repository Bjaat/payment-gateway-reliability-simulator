package dev.bhavy.paymentsim.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The token must look like {@code tok_...}: a 16-digit card number or CVV cannot pass validation,
 * so real card data is rejected at the API boundary.
 */
public record CreatePaymentRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must be alphanumeric, '_' or '-'")
        String merchantId,

        @NotNull @Min(1) @Max(100_000_000L)
        Long amountMinor,

        @NotBlank @Pattern(regexp = "^(?i)(INR|USD|EUR|GBP)$", message = "must be one of INR, USD, EUR, GBP")
        String currency,

        @NotBlank @Size(max = 64) @Pattern(regexp = "^tok_[a-z0-9_]+$", message = "must be a synthetic token such as tok_success")
        String paymentMethodToken) {
}
