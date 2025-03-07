package com.yaz.kyotaidoshin.core.service.telegram;

import com.yaz.kyotaidoshin.core.client.telegram.ReplyParameters;
import com.yaz.kyotaidoshin.core.client.telegram.SendDocument;
import com.yaz.kyotaidoshin.util.RandomUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@QuarkusTest
class TelegramRestServiceTest {

  @Inject
  TelegramRestService service;

  @Test
  void sendDocument() {

    final var updates = service.getUpdates().await().atMost(Duration.ofMinutes(3));
    log.info("updates: {}", updates);
    final var update = updates.get(0);

    final var sendDocument = SendDocument.builder()
        .chatId(update.message().chat().id())
        .caption(RandomUtil.randomStr(10))
        .document(new File("Dockerfile.flyio"))
        .replyParameters(ReplyParameters.replyTo(update.message().messageId()))
        .build();

    service.sendDocument(sendDocument).await().atMost(Duration.ofMinutes(3));
  }

}