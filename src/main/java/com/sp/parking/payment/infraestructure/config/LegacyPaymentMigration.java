package com.sp.parking.payment.infraestructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Los pagos anteriores a los turnos de caja guardaban el metodo "DIGITAL", que ahora es ONLINE,
 * y no tenian concepto. Se ajustan al arrancar; si ya estan al dia no cambia nada.
 */
@Component
@RequiredArgsConstructor
public class LegacyPaymentMigration implements ApplicationRunner {
  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(ApplicationArguments args) {
    jdbcTemplate.update("update payment set method = 'ONLINE' where method = 'DIGITAL'");
    jdbcTemplate.update("update payment set concept = 'PARKING' where concept is null");
  }
}
