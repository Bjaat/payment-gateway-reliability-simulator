package dev.bhavy.paymentsim.service;

import dev.bhavy.paymentsim.domain.AttemptOutcome;
import dev.bhavy.paymentsim.domain.Payment;
import dev.bhavy.paymentsim.domain.PaymentAttempt;
import dev.bhavy.paymentsim.domain.PaymentStatus;
import dev.bhavy.paymentsim.provider.PaymentProvider;
import dev.bhavy.paymentsim.provider.ProviderResult;
import dev.bhavy.paymentsim.repository.PaymentAttemptRepository;
import dev.bhavy.paymentsim.repository.PaymentRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Not annotated @Transactional on purpose: each repository call commits on its own, so the unique-constraint
 * violation on a concurrent duplicate surfaces immediately and attempt history is persisted attempt by attempt.
 */
@Service
public class PaymentService {

    public static final String RETRIES_EXHAUSTED = "RETRIES_EXHAUSTED";

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentProvider provider;
    private final RetryProperties retry;

    public PaymentService(PaymentRepository payments, PaymentAttemptRepository attempts,
                          PaymentProvider provider, RetryProperties retry) {
        this.payments = payments;
        this.attempts = attempts;
        this.provider = provider;
        this.retry = retry;
    }

    public CreateResult create(String idempotencyKey, String merchantId, long amountMinor,
                               String currency, String paymentMethodToken) {
        String normalizedCurrency = currency.trim().toUpperCase();
        String hash = RequestHasher.hash(merchantId, amountMinor, normalizedCurrency, paymentMethodToken);

        Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        Payment payment = new Payment(idempotencyKey, hash, merchantId.trim(), amountMinor,
                normalizedCurrency, paymentMethodToken.trim());
        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException race) {
            // Another request inserted the same key between our lookup and insert.
            Payment winner = payments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
            return replay(winner, hash);
        }

        log.info("payment created paymentId={} merchantId={} amountMinor={} currency={}",
                payment.getId(), payment.getMerchantId(), payment.getAmountMinor(), payment.getCurrency());

        Payment finished = process(payment);
        return new CreateResult(finished, false);
    }

    public Payment get(String paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public List<PaymentAttempt> attemptsFor(String paymentId) {
        get(paymentId);
        return attempts.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
    }

    public long attemptCount(String paymentId) {
        return attempts.countByPaymentId(paymentId);
    }

    private CreateResult replay(Payment existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            log.warn("idempotency key reused with different payload paymentId={}", existing.getId());
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        log.info("idempotent replay paymentId={} status={}", existing.getId(), existing.getStatus());
        return new CreateResult(existing, true);
    }

    private Payment process(Payment created) {
        Payment payment = created;
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment = payments.saveAndFlush(payment);

        for (int attemptNumber = 1; attemptNumber <= retry.maxAttempts(); attemptNumber++) {
            ProviderResult result = provider.charge(payment.getPaymentMethodToken(),
                    payment.getAmountMinor(), attemptNumber);
            attempts.saveAndFlush(new PaymentAttempt(payment.getId(), attemptNumber,
                    result.outcome(), result.errorCode()));
            log.info("provider attempt paymentId={} attempt={} outcome={} errorCode={}",
                    payment.getId(), attemptNumber, result.outcome(), result.errorCode());

            if (result.outcome() == AttemptOutcome.SUCCESS) {
                payment.transitionTo(PaymentStatus.SUCCEEDED);
                return finish(payment);
            }
            if (result.outcome() == AttemptOutcome.PERMANENT_DECLINE) {
                payment.transitionTo(PaymentStatus.DECLINED);
                payment.setFailureCode(result.errorCode());
                return finish(payment);
            }

            boolean hasMoreAttempts = attemptNumber < retry.maxAttempts();
            if (!hasMoreAttempts) {
                break;
            }
            if (!backoff(attemptNumber)) {
                break;
            }
        }

        payment.transitionTo(PaymentStatus.FAILED);
        payment.setFailureCode(RETRIES_EXHAUSTED);
        return finish(payment);
    }

    private Payment finish(Payment payment) {
        Payment saved = payments.saveAndFlush(payment);
        log.info("payment finished paymentId={} status={} failureCode={}",
                saved.getId(), saved.getStatus(), saved.getFailureCode());
        return saved;
    }

    /** Linear backoff. Returns false if interrupted, in which case retrying stops. */
    private boolean backoff(int attemptNumber) {
        long sleepMs = retry.backoffMs() * attemptNumber;
        if (sleepMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(sleepMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
