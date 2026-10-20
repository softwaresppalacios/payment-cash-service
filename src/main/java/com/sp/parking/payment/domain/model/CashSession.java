package com.sp.parking.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Turno de caja de un parqueadero. Solo hay uno abierto a la vez; los pagos se registran en el.
 * Al cerrar se guardan los totales y la diferencia entre el efectivo contado y el esperado.
 */
@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashSession {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true, updatable = false)
  private UUID uuid;
  @Column(nullable = false)
  private UUID tenantId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CashSessionStatus status;

  @Column(nullable = false)
  private Instant openedAt;
  private UUID openedByUuid;
  private String openedByName;
  /** Base de efectivo con la que arranca el turno. */
  @Column(nullable = false)
  private BigDecimal openingFloat;

  private Instant closedAt;
  private UUID closedByUuid;
  private String closedByName;
  private BigDecimal cashTotal;
  private BigDecimal onlineTotal;
  private BigDecimal parkingTotal;
  private BigDecimal monthlyTotal;
  /** Base + cobros en efectivo. */
  private BigDecimal expectedCash;
  private BigDecimal countedCash;
  /** Contado - esperado: negativo es faltante, positivo sobrante. */
  private BigDecimal cashDifference;
  private Integer paymentsCount;
  private Integer vehiclesCount;
  @Column(length = 500)
  private String notes;

  @PrePersist
  void prePersist() {
    if (uuid == null) uuid = UUID.randomUUID();
    if (openedAt == null) openedAt = Instant.now();
    if (status == null) status = CashSessionStatus.OPEN;
    if (openingFloat == null) openingFloat = BigDecimal.ZERO;
  }
}
