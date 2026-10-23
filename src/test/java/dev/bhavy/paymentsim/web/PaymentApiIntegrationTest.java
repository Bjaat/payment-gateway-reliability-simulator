package dev.bhavy.paymentsim.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bhavy.paymentsim.repository.PaymentAttemptRepository;
import dev.bhavy.paymentsim.repository.PaymentRepository;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiIntegrationTest {

    private static final String URL = "/api/v1/payments";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;

    @BeforeEach
    void clean() {
        attempts.deleteAll();
        payments.deleteAll();
    }

    private static String body(String token) {
        return body("merchant_1", 2500, "INR", token);
    }

    private static String body(String merchant, long amount, String currency, String token) {
        return """
                {"merchantId":"%s","amountMinor":%d,"currency":"%s","paymentMethodToken":"%s"}
                """.formatted(merchant, amount, currency, token);
    }

    private MvcResult create(String key, String body) throws Exception {
        return mvc.perform(post(URL)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // ---- successful payment ----

    @Test
    void successfulPaymentIsCreatedRetrievedAndHasOneAttempt() throws Exception {
        MvcResult created = mvc.perform(post(URL)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_success")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.amountMinor").value(2500))
                .andReturn();

        String id = idOf(created);

        mvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mvc.perform(get(URL + "/" + id + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].attemptNumber").value(1))
                .andExpect(jsonPath("$[0].outcome").value("SUCCESS"));
    }

    // ---- idempotency ----

    @Test
    void repeatedRequestWithSameKeyAndPayloadReturnsOriginalPaymentWithoutDuplicate() throws Exception {
        String key = "key-" + UUID.randomUUID();

        MvcResult first = create(key, body("tok_success"));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = mvc.perform(post(URL)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_success")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(payments.count()).isEqualTo(1);
        assertThat(attempts.count()).isEqualTo(1);
    }

    @Test
    void reusingKeyWithDifferentPayloadIsRejected() throws Exception {
        String key = "key-" + UUID.randomUUID();
        create(key, body("merchant_1", 2500, "INR", "tok_success"));

        mvc.perform(post(URL)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("merchant_1", 9999, "INR", "tok_success")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key reuse"));

        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void differentKeysCreateDistinctPayments() throws Exception {
        create("key-a-" + UUID.randomUUID(), body("tok_success"));
        create("key-b-" + UUID.randomUUID(), body("tok_success"));

        assertThat(payments.count()).isEqualTo(2);
    }

    // ---- validation ----

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_success")))
                .andExpect(status().isBadRequest());

        assertThat(payments.count()).isZero();
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("zero amount", body("merchant_1", 0, "INR", "tok_success")),
                Arguments.of("negative amount", body("merchant_1", -5, "INR", "tok_success")),
                Arguments.of("amount above limit", body("merchant_1", 100_000_001L, "INR", "tok_success")),
                Arguments.of("unsupported currency", body("merchant_1", 100, "XYZ", "tok_success")),
                Arguments.of("blank merchant", body(" ", 100, "INR", "tok_success")),
                Arguments.of("raw card-number-like token", body("merchant_1", 100, "INR", "4111111111111111")),
                Arguments.of("token without tok_ prefix", body("merchant_1", 100, "INR", "success")),
                Arguments.of("missing fields", "{}"),
                Arguments.of("malformed json", "{not json"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void invalidInputsAreRejectedAndNothingIsStored(String name, String invalidBody) throws Exception {
        mvc.perform(post(URL)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        assertThat(payments.count()).isZero();
        assertThat(attempts.count()).isZero();
    }

    // ---- retries and failures ----

    @Test
    void transientFailureIsRetriedAndEventuallySucceeds() throws Exception {
        MvcResult result = mvc.perform(post(URL)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_flaky_once")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attemptCount").value(2))
                .andReturn();

        mvc.perform(get(URL + "/" + idOf(result) + "/attempts"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].outcome").value("TRANSIENT_FAILURE"))
                .andExpect(jsonPath("$[0].errorCode").value("PROVIDER_UNAVAILABLE_SIM"))
                .andExpect(jsonPath("$[1].outcome").value("SUCCESS"));
    }

    @Test
    void successOnTheLastAllowedAttemptStillSucceeds() throws Exception {
        mvc.perform(post(URL)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_flaky_twice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attemptCount").value(3));
    }

    @Test
    void retriesAreBoundedAndPaymentFailsWhenExhausted() throws Exception {
        MvcResult result = mvc.perform(post(URL)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_timeout")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("RETRIES_EXHAUSTED"))
                .andExpect(jsonPath("$.attemptCount").value(3))
                .andReturn();

        mvc.perform(get(URL + "/" + idOf(result) + "/attempts"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].outcome").value("TRANSIENT_FAILURE"));
    }

    @Test
    void permanentDeclineIsNotRetried() throws Exception {
        mvc.perform(post(URL)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_decline")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.failureCode").value("DECLINED_BY_ISSUER_SIM"))
                .andExpect(jsonPath("$.attemptCount").value(1));

        assertThat(attempts.count()).isEqualTo(1);
    }

    @Test
    void replayOfFailedPaymentDoesNotRetryAgain() throws Exception {
        String key = "key-" + UUID.randomUUID();
        create(key, body("tok_timeout"));
        long attemptsAfterFirst = attempts.count();

        mvc.perform(post(URL)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tok_timeout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(attempts.count()).isEqualTo(attemptsAfterFirst);
    }

    // ---- retrieval and health ----

    @Test
    void unknownPaymentReturnsNotFound() throws Exception {
        mvc.perform(get(URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get(URL + "/" + UUID.randomUUID() + "/attempts"))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void responsesCarryACorrelationId() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "trace-123"))
                .andExpect(header().string("X-Correlation-Id", "trace-123"));
    }
}
