package com.sp.parking.payment.domain.repository;

import com.sp.parking.payment.domain.model.CashSession;
import com.sp.parking.payment.domain.model.CashSessionStatus;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {
  Optional<CashSession> findFirstByTenantIdAndStatus(UUID tenantId, CashSessionStatus status);
  Optional<CashSession> findByTenantIdAndUuid(UUID tenantId, UUID uuid);
  List<CashSession> findByTenantIdAndOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDesc(UUID tenantId, Instant from, Instant to);
}
