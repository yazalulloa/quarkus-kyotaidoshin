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
public record SendMessage(
    Long chatId,
    String text,
    ParseMode parseMode,
    Boolean disableWebPagePreview,
    Boolean disableNotification,
    Boolean allowSendingWithoutReply,
    ReplyParameters replyParameters,
    ReplyMarkup replyMarkup
) {


  public SendMessage addText(String text) {
    return this.toBuilder().text(text).build();
  }
}
