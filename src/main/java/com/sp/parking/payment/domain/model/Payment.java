package com.sp.parking.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cobro registrado en un turno de caja. Guarda los datos del vehiculo o la mensualidad
 * para que caja y reportes no dependan de consultar otros servicios.
 */
@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(unique = true)
  private UUID uuid;
  private UUID tenantId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id")
  private CashSession session;
  @Enumerated(EnumType.STRING)
  private PaymentConcept concept;
  /** Ticket (PARKING) o mensualidad (MONTHLY) que origino el cobro. */
  @Column(name = "ticket_uuid")
  private UUID referenceUuid;
  private BigDecimal amount;
  @Enumerated(EnumType.STRING)
  private PaymentMethod method;
  /** Comprobante del pago en linea (Nequi, transferencia...), opcional. */
  private String onlineReference;
  private String plate;
  private String vehicleType;
  private Instant checkedInAt;
  private Instant checkedOutAt;
  private String description;
  private UUID registeredByUuid;
  private String registeredByName;
  private Instant paidAt;

  @PrePersist
  void prePersist() {
    if (uuid == null) uuid = UUID.randomUUID();
    if (paidAt == null) paidAt = Instant.now();
  }
}
