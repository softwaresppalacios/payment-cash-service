package com.sp.parking.payment.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
  private PaymentDtos() {}
  public record CreatePaymentRequest(@NotNull UUID ticketUuid, @NotNull BigDecimal amount, @NotBlank String method) {}
  public record PaymentResponse(Long id, UUID ticketUuid, BigDecimal amount, String method, Instant paidAt) {}
  public record CashMovementResponse(Long id, UUID ticketUuid, BigDecimal amount, String method, Instant paidAt) {}
  public record CashSummaryResponse(BigDecimal todayRevenue, BigDecimal cashRevenue, BigDecimal digitalRevenue, long movements, List<CashMovementResponse> recentMovements) {}
}
