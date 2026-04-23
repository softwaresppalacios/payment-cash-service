package com.sp.parking.payment.web.controller;

import com.sp.parking.payment.application.service.PaymentService;
import com.sp.parking.payment.web.dto.PaymentDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService paymentService;

  @PostMapping("/payments")
  public PaymentResponse create(@RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody CreatePaymentRequest request) {
    return paymentService.create(tenantId, request);
  }

  @GetMapping("/cash-sessions/current")
  public CashSummaryResponse current(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return paymentService.today(tenantId);
  }
}

