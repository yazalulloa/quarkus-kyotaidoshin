package com.yaz.kyotaidoshin.core.service.mailer;

import com.yaz.kyotaidoshin.api.domain.response.building.BuildingInitFormDto.EmailConfig;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import io.micrometer.core.annotation.Timed;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.MailResult;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.mail.MailClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MailerService {

  private final Vertx vertx;
  private final EncryptionService encryptionService;
  private final MailerServiceConfig config;
  private final Map<String, MailerConfig> configMap;
  private final Map<String, MailClient> mailClientMap = new HashMap<>();
  private final List<EmailConfig> emailConfigs = new ArrayList<>();

  public List<EmailConfig> emailConfigs() {
    if (emailConfigs.isEmpty()) {
      configMap.forEach((key, value) -> {
        emailConfigs.add(EmailConfig.builder()
            .id(encryptionService.encrypt(key))
            .key(key)
            .email(value.username())
            .build());
      });
    }

    return emailConfigs;
  }


  public MailClient mailClient(String name) {
//    log.info("mailClient: {}", name);

    final var mailClient = mailClientMap.get(name);
    if (mailClient != null) {
      return mailClient;
    }

    final var config = configMap.get(name);
    if (config == null) {
      throw new IllegalArgumentException("No mailer config found for: " + name);
    }

    final var mailConfig = new MailConfig()
        .setAuthMethods(config.authMethods())
        .setHostname(config.host())
        .setPort(config.port())
        .setSsl(true)
        .setUsername(config.username())
        .setPassword(config.password())
        .setKeepAlive(true)
        .setStarttls(StartTLSOptions.OPTIONAL)
        .setUserAgent("kyotaidoshin");

    final var newMailClient = MailClient.create(vertx, mailConfig);
    mailClientMap.put(name, newMailClient);
    return newMailClient;
  }

  public Uni<Void> send(String key, MailMessage message) {
    message.setFrom(configMap.get(key).username());
    if (config.useAlternativeReceiptTo()) {
      message.setTo(new ArrayList<>(config.receiptTo()));
    }

//    log.info("Acquiring lock for: {}", key);
    return vertx.sharedData().getLockWithTimeout(key, Duration.ofMinutes(2).toMillis())
        .flatMap(lock -> {
//          log.info("Sending mail: {}", message);
          return sendMail(key, message)
              //        .invoke(mailResult -> {
//          log.info("Mail sent: {}", mailResult);
//        })
              .onTermination()
              .invoke(lock::release);
        })
        .replaceWithVoid();
  }

  @Timed(value = "mail.send", description = "Send mail")
  Uni<MailResult> sendMail(String key, MailMessage message) {
    return mailClient(key).sendMail(message);
  }

  public String email(String key) {
    final var config = configMap.get(key);
    if (config != null) {
      return config.username();
    }

    return null;
  }
}
