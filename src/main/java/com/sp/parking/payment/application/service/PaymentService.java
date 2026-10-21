package com.sp.parking.payment.application.service;

import com.sp.parking.payment.domain.model.*;
import com.sp.parking.payment.domain.repository.CashSessionRepository;
import com.sp.parking.payment.domain.repository.PaymentRepository;
import com.sp.parking.payment.web.dto.PaymentDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Cobros y turnos de caja. Cada cobro entra al turno abierto; si no hay ninguno se abre uno
 * automaticamente (base 0) para no frenar la operacion en la puerta.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {
  private static final ZoneId ZONE = ZoneId.of("America/Bogota");

  private final PaymentRepository paymentRepository;
  private final CashSessionRepository sessionRepository;

  /** Usuario en sesion que cobra, abre o cierra la caja. */
  public record Cashier(UUID uuid, String name) {}

  /** Un ticket o una mensualidad se cobra una sola vez: si ya existe el pago se devuelve el mismo. */
  @Transactional
  public PaymentResponse register(UUID tenantId, Cashier cashier, RegisterPaymentRequest request) {
    Optional<Payment> existing = paymentRepository.findFirstByTenantIdAndConceptAndReferenceUuid(tenantId, request.concept(), request.referenceUuid());
    if (existing.isPresent()) return toResponse(existing.get());

    CashSession session = sessionRepository.findFirstByTenantIdAndStatus(tenantId, CashSessionStatus.OPEN)
      .orElseGet(() -> sessionRepository.save(CashSession.builder()
        .tenantId(tenantId)
        .openedByUuid(cashier.uuid())
        .openedByName(cashier.name())
        .openingFloat(BigDecimal.ZERO)
        .build()));

    Payment payment = paymentRepository.save(Payment.builder()
      .tenantId(tenantId)
      .session(session)
      .concept(request.concept())
      .referenceUuid(request.referenceUuid())
      .amount(request.amount())
      .method(request.method())
      .onlineReference(request.method() == PaymentMethod.ONLINE ? blankToNull(request.onlineReference()) : null)
      .plate(request.plate())
      .vehicleType(request.vehicleType())
      .checkedInAt(request.checkedInAt())
      .checkedOutAt(request.checkedOutAt())
      .description(blankToNull(request.description()))
      .registeredByUuid(cashier.uuid())
      .registeredByName(cashier.name())
      .build());
    return toResponse(payment);
  }

  @Transactional(readOnly = true)
  public CurrentCashResponse current(UUID tenantId) {
    return new CurrentCashResponse(sessionRepository.findFirstByTenantIdAndStatus(tenantId, CashSessionStatus.OPEN)
      .map(session -> toResponse(session, paymentRepository.findBySessionIdOrderByPaidAtAsc(session.getId()), true))
      .orElse(null));
  }

  @Transactional
  public CashSessionResponse open(UUID tenantId, Cashier cashier, OpenCashSessionRequest request) {
    if (sessionRepository.findFirstByTenantIdAndStatus(tenantId, CashSessionStatus.OPEN).isPresent()) {
      throw new ResponseStatusException(CONFLICT, "Ya hay una caja abierta; ciérrala antes de abrir otra");
    }
    CashSession session = sessionRepository.save(CashSession.builder()
      .tenantId(tenantId)
      .openedByUuid(cashier.uuid())
      .openedByName(cashier.name())
      .openingFloat(request.openingFloat())
      .build());
    return toResponse(session, List.of(), true);
  }

  @Transactional
  public CashSessionResponse close(UUID tenantId, Cashier cashier, CloseCashSessionRequest request) {
    CashSession session = sessionRepository.findFirstByTenantIdAndStatus(tenantId, CashSessionStatus.OPEN)
      .orElseThrow(() -> new ResponseStatusException(CONFLICT, "No hay una caja abierta para cerrar"));
    List<Payment> payments = paymentRepository.findBySessionIdOrderByPaidAtAsc(session.getId());
    CashTotals totals = totals(payments);
    BigDecimal expected = session.getOpeningFloat().add(totals.cash());

    session.setStatus(CashSessionStatus.CLOSED);
    session.setClosedAt(Instant.now());
    session.setClosedByUuid(cashier.uuid());
    session.setClosedByName(cashier.name());
    session.setCashTotal(totals.cash());
    session.setOnlineTotal(totals.online());
    session.setParkingTotal(totals.parking());
    session.setMonthlyTotal(totals.monthly());
    session.setPaymentsCount(totals.payments());
    session.setVehiclesCount(totals.vehicles());
    session.setExpectedCash(expected);
    session.setCountedCash(request.countedCash());
    session.setCashDifference(request.countedCash().subtract(expected));
    session.setNotes(blankToNull(request.notes()));
    return toResponse(session, payments, true);
  }

  /** Turnos abiertos en el rango de fechas (dias de Colombia), del mas reciente al mas antiguo. */
  @Transactional(readOnly = true)
  public List<CashSessionResponse> history(UUID tenantId, LocalDate from, LocalDate to, CashSessionStatus status, String responsible) {
    List<CashSession> sessions = sessionRepository.findByTenantIdAndOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDesc(
      tenantId, from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant());
    List<CashSession> filtered = sessions.stream()
      .filter(session -> status == null || session.getStatus() == status)
      .filter(session -> responsible == null || responsible.isBlank() || responsible.equals(responsibleOf(session)))
      .toList();
    // Los turnos abiertos aun no tienen totales guardados: se calculan con sus pagos.
    List<Long> openIds = filtered.stream().filter(session -> session.getStatus() == CashSessionStatus.OPEN).map(CashSession::getId).toList();
    Map<Long, List<Payment>> openPayments = openIds.isEmpty() ? Map.of()
      : paymentRepository.findBySessionIdIn(openIds).stream().collect(Collectors.groupingBy(payment -> payment.getSession().getId()));
    return filtered.stream()
      .map(session -> toResponse(session, openPayments.getOrDefault(session.getId(), List.of()), false))
      .toList();
  }

  /** Cobros (parqueo y mensualidades) pagados en el rango de fechas, dias de Colombia; del mas reciente al mas antiguo. */
  @Transactional(readOnly = true)
  public List<PaymentResponse> payments(UUID tenantId, LocalDate from, LocalDate to) {
    if (to.isBefore(from)) throw new ResponseStatusException(BAD_REQUEST, "La fecha inicio debe ser anterior a la fecha fin");
    if (from.plusDays(366).isBefore(to)) throw new ResponseStatusException(BAD_REQUEST, "El rango máximo es de un año");
    return paymentRepository.findByTenantIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
        tenantId, from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant()).stream()
      .sorted(Comparator.comparing(Payment::getPaidAt).reversed())
      .map(this::toResponse)
      .toList();
  }

  @Transactional(readOnly = true)
  public CashSessionResponse detail(UUID tenantId, UUID sessionUuid) {
    CashSession session = sessionRepository.findByTenantIdAndUuid(tenantId, sessionUuid)
      .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Turno de caja no encontrado"));
    return toResponse(session, paymentRepository.findBySessionIdOrderByPaidAtAsc(session.getId()), true);
  }

  @Transactional(readOnly = true)
  public TodaySummaryResponse today(UUID tenantId) {
    LocalDate today = LocalDate.now(ZONE);
    CashTotals todayTotals = totals(paymentsOn(tenantId, today));
    CashTotals yesterdayTotals = totals(paymentsOn(tenantId, today.minusDays(1)));
    return new TodaySummaryResponse(todayTotals.total(), todayTotals.cash(), todayTotals.online(), todayTotals.payments(), yesterdayTotals.total());
  }

  private List<Payment> paymentsOn(UUID tenantId, LocalDate day) {
    return paymentRepository.findByTenantIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
      tenantId, day.atStartOfDay(ZONE).toInstant(), day.plusDays(1).atStartOfDay(ZONE).toInstant());
  }

  private CashTotals totals(List<Payment> payments) {
    BigDecimal cash = BigDecimal.ZERO, online = BigDecimal.ZERO, parking = BigDecimal.ZERO, monthly = BigDecimal.ZERO;
    int vehicles = 0;
    for (Payment payment : payments) {
      BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
      if (payment.getMethod() == PaymentMethod.ONLINE) online = online.add(amount); else cash = cash.add(amount);
      if (payment.getConcept() == PaymentConcept.MONTHLY) {
        monthly = monthly.add(amount);
      } else {
        parking = parking.add(amount);
        vehicles++;
      }
    }
    return new CashTotals(cash, online, parking, monthly, cash.add(online), payments.size(), vehicles);
  }

  /** Quien cerro el turno; si sigue abierto, quien lo abrio. */
  private String responsibleOf(CashSession session) {
    return session.getClosedByName() != null ? session.getClosedByName() : session.getOpenedByName();
  }

  private CashSessionResponse toResponse(CashSession session, List<Payment> payments, boolean includePayments) {
    boolean closed = session.getStatus() == CashSessionStatus.CLOSED;
    CashTotals totals = closed
      ? new CashTotals(
          zero(session.getCashTotal()), zero(session.getOnlineTotal()), zero(session.getParkingTotal()), zero(session.getMonthlyTotal()),
          zero(session.getCashTotal()).add(zero(session.getOnlineTotal())),
          session.getPaymentsCount() == null ? 0 : session.getPaymentsCount(),
          session.getVehiclesCount() == null ? 0 : session.getVehiclesCount())
      : totals(payments);
    BigDecimal expected = closed ? session.getExpectedCash() : session.getOpeningFloat().add(totals.cash());
    return new CashSessionResponse(
      session.getUuid(),
      session.getStatus().name(),
      session.getOpenedAt(),
      session.getOpenedByName(),
      session.getOpeningFloat(),
      session.getClosedAt(),
      session.getClosedByName(),
      totals,
      expected,
      session.getCountedCash(),
      session.getCashDifference(),
      session.getNotes(),
      includePayments ? payments.stream().map(this::toResponse).toList() : null
    );
  }

  private PaymentResponse toResponse(Payment payment) {
    return new PaymentResponse(
      payment.getUuid(),
      payment.getConcept() == null ? PaymentConcept.PARKING.name() : payment.getConcept().name(),
      payment.getReferenceUuid(),
      payment.getAmount(),
      payment.getMethod() == null ? PaymentMethod.CASH.name() : payment.getMethod().name(),
      payment.getOnlineReference(),
      payment.getPlate(),
      payment.getVehicleType(),
      payment.getCheckedInAt(),
      payment.getCheckedOutAt(),
      payment.getDescription(),
      payment.getRegisteredByName(),
      payment.getPaidAt()
    );
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
