package dev.bhavy.paymentsim.repository;

import dev.bhavy.paymentsim.domain.PaymentAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(String paymentId);

    long countByPaymentId(String paymentId);
}
