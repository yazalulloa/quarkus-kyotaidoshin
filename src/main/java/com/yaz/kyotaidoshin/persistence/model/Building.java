package com.yaz.kyotaidoshin.persistence.model;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.yaz.kyotaidoshin.util.StringUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record Building(
    String id,
    String name,
    String rif,
    String mainCurrency,
    String debtCurrency,
    Set<String> currenciesToShowAmountToPay,
    boolean fixedPay,
    BigDecimal fixedPayAmount,
    Boolean roundUpPayments,
    String emailConfigId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String configEmail,
    Long aptCount) {

  private static final String CARD_ID_PREFIX = "buildings-card-id-";

  private static String cardId() {
    return CARD_ID_PREFIX + UUID.randomUUID();
  }

  public Keys keys() {
    return new Keys(id, 0, cardId());
  }

  public Keys keysWithHash() {
    final var building = this.toBuilder()
        .createdAt(null)
        .updatedAt(null)
        .aptCount(null)
        .configEmail(null)
        .build();
    return new Keys(id, StringUtil.objHash(building), cardId());
  }

  public record Keys(
      String id,
      long hash,
      String cardId) {

  }
}
