package com.yaz.kyotaidoshin.core.service.mailer;

import com.fasterxml.jackson.databind.MappingIterator;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import java.io.IOException;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailerProducer {

  @Produces
  @ApplicationScoped
  MailerService producesMailerService(
      Vertx vertx,
      EncryptionService encryptionService,
      MailerServiceConfig config) throws IOException {

    final var decrypted = encryptionService.decrypt(config.configs());
    final var map = new HashMap<String, MailerConfig>();
    try (MappingIterator<MailerConfig> iterator = DatabindCodec.mapper().readerFor(MailerConfig.class)
        .readValues(decrypted)) {

      while (iterator.hasNext()) {
        final var configItem = iterator.next();
        map.put(configItem.key(), configItem);
      }
    }

    return new MailerService(vertx, encryptionService, config, map);
  }

}
