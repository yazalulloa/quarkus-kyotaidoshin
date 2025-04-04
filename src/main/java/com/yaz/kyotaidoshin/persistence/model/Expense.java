package com.yaz.kyotaidoshin.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.yaz.kyotaidoshin.persistence.model.domain.ExpenseType;
import com.yaz.kyotaidoshin.util.StringUtil;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Expense(
    String buildingId,
    long receiptId,
    long id,
    String description,
    BigDecimal amount,
    String currency,
    boolean reserveFund,
    ExpenseType type
) {

  private static final String CARD_ID_PREFIX = "expense-card-id-";

  private static String cardId() {
    return CARD_ID_PREFIX + UUID.randomUUID();
  }

  public Keys keys() {
    return keys(cardId());
  }

  public Keys keys(String cardId) {
    return new Keys(buildingId, receiptId, id, cardId, StringUtil.objHash(this));
  }


  @Builder(toBuilder = true)
  public record Keys(
      String buildingId,
      long receiptId,
      long id,
      String cardId,
      long hash) {

    public static Keys of(String buildingId, long receiptId) {
      return new Keys(buildingId, receiptId, 0, Expense.cardId(), 0);
    }
  }
}
