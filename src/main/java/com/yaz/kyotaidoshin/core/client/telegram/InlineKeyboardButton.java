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
public record InlineKeyboardButton(
    String text,
    String url,
    LoginUrl loginUrl,
    String callbackData,
    String switchInlineQuery,
    String switchInlineQueryCurrentChat,
    Boolean pay
) implements ReplyMarkup {

  public static InlineKeyboardButton url(String text, String url) {
    return InlineKeyboardButton.builder()
        .text(text)
        .url(url)
        .build();
  }

  public static InlineKeyboardButton loginUrl(String text, LoginUrl loginUrl) {
    return InlineKeyboardButton.builder()
        .text(text)
        .loginUrl(loginUrl)
        .build();
  }

  public static InlineKeyboardButton callbackData(String text, String callbackData) {
    return InlineKeyboardButton.builder()
        .text(text)
        .callbackData(callbackData)
        .build();
  }

  public static InlineKeyboardButton switchInlineQuery(String text, String switchInlineQuery) {
    return InlineKeyboardButton.builder()
        .text(text)
        .switchInlineQuery(switchInlineQuery)
        .build();
  }

  public static InlineKeyboardButton switchInlineQueryCurrentChat(String text, String switchInlineQueryCurrentChat) {
    return InlineKeyboardButton.builder()
        .text(text)
        .switchInlineQueryCurrentChat(switchInlineQueryCurrentChat)
        .build();
  }

  public static InlineKeyboardButton pay(String text) {
    return InlineKeyboardButton.builder()
        .text(text)
        .pay(true)
        .build();
  }

}
