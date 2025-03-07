package com.yaz.kyotaidoshin.persistence.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;


@Builder(toBuilder = true)
public record OidcDbToken(
    String id,
    String idToken,
    String accessToken,
    String refreshToken,
    Long expiresIn,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,

    User user) {


  private static final String CARD_ID_PREFIX = "session-card-id-";

  private static String cardId() {
    return CARD_ID_PREFIX + UUID.randomUUID();
  }

  public Keys keys() {
    return new Keys(id, cardId());
  }

  public record Keys(
      String id,
      String cardId
  ) {

  }
}
