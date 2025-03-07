package com.yaz.kyotaidoshin.core.service.telegram;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TelegramUpdateJobTest {

  @Inject
  TelegramUpdateJob telegramUpdateJob;


  @Test
  void run() {
    telegramUpdateJob.processUpdates(null).await().atMost(Duration.ofMinutes(3));
  }


}