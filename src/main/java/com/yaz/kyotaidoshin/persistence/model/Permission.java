package com.yaz.kyotaidoshin.persistence.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Permission(
    String userId,
    String type,
    LocalDateTime createdAt,

    User user
) {

  private static final String CARD_ID_PREFIX = "permission-card-id-";

  private static String cardId() {
    return CARD_ID_PREFIX + UUID.randomUUID();
  }

  public Keys keys() {
    return new Keys(userId, type, createdAt, cardId());
  }

  public record Keys(
      String userId,
      String type,
      LocalDateTime createdAt,
      String cardId
  ) {

  }

}
