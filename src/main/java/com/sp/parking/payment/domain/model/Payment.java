package com.sp.parking.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private UUID tenantId;
  private UUID ticketUuid;
  private BigDecimal amount;
  private String method;
  private Instant paidAt;

  @PrePersist
  void prePersist() {
    if (paidAt == null) paidAt = Instant.now();
  }
}

