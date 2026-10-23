package dev.bhavy.paymentsim.web;

import dev.bhavy.paymentsim.domain.Payment;
import dev.bhavy.paymentsim.service.CreateResult;
import dev.bhavy.paymentsim.service.PaymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final String REPLAY_HEADER = "Idempotent-Replayed";

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new InvalidRequestException(IDEMPOTENCY_HEADER + " header is required (1-255 characters)");
        }

        CreateResult result = service.create(idempotencyKey.trim(), request.merchantId(),
                request.amountMinor(), request.currency(), request.paymentMethodToken());
        Payment payment = result.payment();
        PaymentResponse body = PaymentResponse.from(payment, service.attemptCount(payment.getId()));

        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAY_HEADER, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/payments/" + payment.getId()))
                .body(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        Payment payment = service.get(id);
        return PaymentResponse.from(payment, service.attemptCount(id));
    }

    @GetMapping("/{id}/attempts")
    public List<AttemptResponse> attempts(@PathVariable String id) {
        return service.attemptsFor(id).stream().map(AttemptResponse::from).toList();
    }
}
