package com.yaz.kyotaidoshin.core.client.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@ToString
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TelegramUser {

  @JsonProperty
  private final String languageCode;

  @JsonProperty
  private final String lastName;

  @JsonProperty
  private final long id;

  @JsonProperty
  private final boolean isBot;

  @JsonProperty
  private final String firstName;

  @JsonProperty
  private final String username;
}