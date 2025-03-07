package com.yaz.kyotaidoshin.core.service.telegram;

import io.vertx.core.MultiMap;

public record TelegramWebhookRequest(MultiMap headers, String body) {

}
