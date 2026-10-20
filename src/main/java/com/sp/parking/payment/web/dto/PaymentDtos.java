package com.sp.parking.payment.web.dto;

import com.sp.parking.payment.domain.model.PaymentConcept;
import com.sp.parking.payment.domain.model.PaymentMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
  private PaymentDtos() {}

  /** Lo envia parking-operation-service al registrar una salida o una mensualidad. */
  public record RegisterPaymentRequest(
    @NotNull(message = "Indica el concepto del pago") PaymentConcept concept,
    @NotNull(message = "Indica el ticket o la mensualidad del pago") UUID referenceUuid,
    @NotNull(message = "Indica el valor del pago") @DecimalMin(value = "0.01", message = "El valor del pago debe ser mayor a cero") BigDecimal amount,
    @NotNull(message = "Selecciona el método de pago") PaymentMethod method,
    @Size(max = 80, message = "La referencia del pago es demasiado larga") String onlineReference,
    @Size(max = 12) String plate,
    @Size(max = 20) String vehicleType,
    Instant checkedInAt,
    Instant checkedOutAt,
    @Size(max = 200) String description
  ) {}

  public record OpenCashSessionRequest(
    @NotNull(message = "Ingresa la base de efectivo") @DecimalMin(value = "0", message = "La base no puede ser negativa") BigDecimal openingFloat
  ) {}

  public record CloseCashSessionRequest(
    @NotNull(message = "Ingresa el efectivo contado") @DecimalMin(value = "0", message = "El efectivo contado no puede ser negativo") BigDecimal countedCash,
    @Size(max = 500, message = "Las observaciones son demasiado largas") String notes
  ) {}

  public record PaymentResponse(
    UUID uuid,
    String concept,
    UUID referenceUuid,
    BigDecimal amount,
    String method,
    String onlineReference,
    String plate,
    String vehicleType,
    Instant checkedInAt,
    Instant checkedOutAt,
    String description,
    String registeredByName,
    Instant paidAt
  ) {}

  /** vehicles: cobros por horas o dia (cada uno es una salida). */
  public record CashTotals(BigDecimal cash, BigDecimal online, BigDecimal parking, BigDecimal monthly, BigDecimal total, int payments, int vehicles) {}

  /** payments solo viene en la caja actual y en el detalle de un turno, no en el historial. */
  public record CashSessionResponse(
    UUID uuid,
    String status,
    Instant openedAt,
    String openedByName,
    BigDecimal openingFloat,
    Instant closedAt,
    String closedByName,
    CashTotals totals,
    BigDecimal expectedCash,
    BigDecimal countedCash,
    BigDecimal cashDifference,
    String notes,
    List<PaymentResponse> payments
  ) {}

  /** session es null cuando no hay caja abierta. */
  public record CurrentCashResponse(CashSessionResponse session) {}

  /** Ingresos del dia calendario (todos los turnos), para el dashboard. */
  public record TodaySummaryResponse(BigDecimal todayRevenue, BigDecimal cashRevenue, BigDecimal onlineRevenue, int payments, BigDecimal yesterdayRevenue) {}
}
