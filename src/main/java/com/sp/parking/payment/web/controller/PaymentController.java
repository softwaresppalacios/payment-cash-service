package com.sp.parking.payment.web.controller;

import com.sp.parking.payment.infraestructure.client.AuditClient;
import com.sp.parking.payment.application.service.PaymentService;
import com.sp.parking.payment.application.service.PaymentService.Cashier;
import com.sp.parking.payment.domain.model.CashSessionStatus;
import com.sp.parking.payment.infraestructure.security.RequireRole;
import com.sp.parking.payment.infraestructure.security.TenantAccessFilter;
import com.sp.parking.payment.web.dto.PaymentDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService paymentService;
  private final AuditClient auditClient;

  @PostMapping("/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentResponse register(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestAttribute(TenantAccessFilter.USER_ATTRIBUTE) UUID userUuid,
    @RequestAttribute(TenantAccessFilter.USER_NAME_ATTRIBUTE) String userName,
    @Valid @RequestBody RegisterPaymentRequest request
  ) {
    return paymentService.register(tenantId, new Cashier(userUuid, userName), request);
  }

  /** Cobros por rango de fechas para reportes; por defecto el mes en curso. */
  @GetMapping("/payments")
  @RequireRole({"OWNER", "ADMIN"})
  public List<PaymentResponse> payments(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
    return paymentService.payments(tenantId, from == null ? today.withDayOfMonth(1) : from, to == null ? today : to);
  }

  @GetMapping("/payments/today")
  public TodaySummaryResponse today(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return paymentService.today(tenantId);
  }

  @GetMapping("/cash-sessions/current")
  public CurrentCashResponse current(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return paymentService.current(tenantId);
  }

  @PostMapping("/cash-sessions/open")
  @ResponseStatus(HttpStatus.CREATED)
  public CashSessionResponse open(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestAttribute(TenantAccessFilter.USER_ATTRIBUTE) UUID userUuid,
    @RequestAttribute(TenantAccessFilter.USER_NAME_ATTRIBUTE) String userName,
    @Valid @RequestBody OpenCashSessionRequest request
  ) {
    CashSessionResponse opened = paymentService.open(tenantId, new Cashier(userUuid, userName), request);
    auditClient.record(tenantId, "CASH_OPEN", "CASH_SESSION", "Turno " + AuditClient.when(opened.openedAt()), "Base inicial " + AuditClient.money(opened.openingFloat()));
    return opened;
  }

  @PostMapping("/cash-sessions/current/close")
  public CashSessionResponse close(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestAttribute(TenantAccessFilter.USER_ATTRIBUTE) UUID userUuid,
    @RequestAttribute(TenantAccessFilter.USER_NAME_ATTRIBUTE) String userName,
    @Valid @RequestBody CloseCashSessionRequest request
  ) {
    CashSessionResponse closed = paymentService.close(tenantId, new Cashier(userUuid, userName), request);
    auditClient.record(tenantId, "CASH_CLOSE", "CASH_SESSION", "Turno " + AuditClient.when(closed.openedAt()), String.join(" · ",
      "Total " + AuditClient.money(closed.totals() == null ? null : closed.totals().total()),
      "Efectivo esperado " + AuditClient.money(closed.expectedCash()),
      "Contado " + AuditClient.money(closed.countedCash()),
      "Diferencia " + AuditClient.money(closed.cashDifference())));
    return closed;
  }

  /** Historial de turnos; por defecto el mes en curso. */
  @GetMapping("/cash-sessions")
  @RequireRole({"OWNER", "ADMIN"})
  public List<CashSessionResponse> history(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
    @RequestParam(value = "status", required = false) CashSessionStatus status,
    @RequestParam(value = "responsible", required = false) String responsible
  ) {
    LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
    return paymentService.history(tenantId, from == null ? today.withDayOfMonth(1) : from, to == null ? today : to, status, responsible);
  }

  @GetMapping("/cash-sessions/{sessionUuid}")
  @RequireRole({"OWNER", "ADMIN"})
  public CashSessionResponse detail(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable("sessionUuid") UUID sessionUuid) {
    return paymentService.detail(tenantId, sessionUuid);
  }
}
