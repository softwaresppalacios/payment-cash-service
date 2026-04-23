package com.sp.parking.payment.application.service;

import com.sp.parking.payment.domain.model.Payment;
import com.sp.parking.payment.domain.repository.PaymentRepository;
import com.sp.parking.payment.web.dto.PaymentDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
  private final PaymentRepository paymentRepository;

  @Transactional
  public PaymentResponse create(UUID tenantId, CreatePaymentRequest request) {
    Payment saved = paymentRepository.save(Payment.builder().tenantId(tenantId).ticketUuid(request.ticketUuid()).amount(request.amount()).method(request.method()).build());
    return new PaymentResponse(saved.getId(), saved.getTicketUuid(), saved.getAmount(), saved.getMethod(), saved.getPaidAt());
  }

  @Transactional(readOnly = true)
  public CashSummaryResponse today(UUID tenantId) {
    Instant from = LocalDate.now(ZoneId.of("America/Bogota")).atStartOfDay(ZoneId.of("America/Bogota")).toInstant();
    java.util.List<Payment> payments = paymentRepository.findByTenantIdAndPaidAtGreaterThanEqualOrderByPaidAtDesc(tenantId, from);
    BigDecimal cash = sumByMethod(payments, "CASH");
    BigDecimal digital = sumByMethod(payments, "DIGITAL");
    BigDecimal total = cash.add(digital);
    return new CashSummaryResponse(
      total,
      cash,
      digital,
      payments.size(),
      payments.stream()
        .limit(50)
        .map(payment -> new CashMovementResponse(payment.getId(), payment.getTicketUuid(), payment.getAmount(), payment.getMethod(), payment.getPaidAt()))
        .toList()
    );
  }

  private BigDecimal sumByMethod(java.util.List<Payment> payments, String method) {
    return payments.stream()
      .filter(payment -> method.equalsIgnoreCase(payment.getMethod()))
      .map(Payment::getAmount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
