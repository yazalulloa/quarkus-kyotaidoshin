package com.yaz.kyotaidoshin.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Rate(
    Long id,
    String fromCurrency,
    String toCurrency,
    BigDecimal rate,
    Source source,
    LocalDate dateOfRate,
    LocalDateTime dateOfFile,
    LocalDateTime createdAt,
    String description,
    Long hash,
    String etag,
    String lastModified) {


  public enum Source {
    BCV, PLATFORM;

    public static final Source[] VALUES = values();
  }

  private static final String CARD_ID_PREFIX = "rates-card-id-";

  private static String cardId() {
    return CARD_ID_PREFIX + UUID.randomUUID();
  }

  public Keys keys() {
    return new Keys(id, cardId());
  }

  public record Keys(
      long id,
      String cardId
  ) {

  }
}
