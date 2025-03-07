package com.yaz.kyotaidoshin.core.client.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReplyParameters(
    long messageId
) {


  public static ReplyParameters replyTo(long messageId) {
    return ReplyParameters.builder()
        .messageId(messageId)
        .build();
  }
}
