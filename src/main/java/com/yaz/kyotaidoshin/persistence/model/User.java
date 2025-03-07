package com.yaz.kyotaidoshin.persistence.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;


@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record User(
    String id,
    String providerId,
    IdentityProvider provider,
    String email,
    String username,
    String firstName,
    String lastName,
    String picture,
    JsonObject data,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt,
    TelegramChat telegramChat,
    Set<NotificationEvent.Event> notificationEvents
) {

  private static final String CARD_ID_PREFIX = "users-card-id-";

  private static String cardId() {
    return CARD_ID_PREFIX + UUID.randomUUID();
  }

  public Keys keys() {
    return new Keys(id, cardId());
  }

  @Builder
  public record TelegramChat(
      Long chatId, String username, String firstName
  ) {

    public boolean hasChat() {
      return chatId != null;
    }

  }

  public record Keys(
      String id,
      String cardId
  ) {

  }
}

