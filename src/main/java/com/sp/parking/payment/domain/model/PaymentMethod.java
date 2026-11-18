package com.sp.parking.payment.domain.model;

/** Efectivo en caja, o pago en linea (por ahora confirmado a mano por el cajero, sin pasarela). */
public enum PaymentMethod {
  CASH,
  ONLINE
}
