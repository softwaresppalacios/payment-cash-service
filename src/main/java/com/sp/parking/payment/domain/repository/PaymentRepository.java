package com.sp.parking.payment.domain.repository;

import com.sp.parking.payment.domain.model.Payment;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  List<Payment> findByTenantIdAndPaidAtGreaterThanEqualOrderByPaidAtDesc(UUID tenantId, Instant from);
}
