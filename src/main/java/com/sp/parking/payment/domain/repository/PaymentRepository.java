package com.sp.parking.payment.domain.repository;

import com.sp.parking.payment.domain.model.Payment;
import com.sp.parking.payment.domain.model.PaymentConcept;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  List<Payment> findBySessionIdOrderByPaidAtAsc(Long sessionId);
  List<Payment> findBySessionIdIn(Collection<Long> sessionIds);
  List<Payment> findByTenantIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(UUID tenantId, Instant from, Instant to);
  Optional<Payment> findFirstByTenantIdAndConceptAndReferenceUuid(UUID tenantId, PaymentConcept concept, UUID referenceUuid);
}
