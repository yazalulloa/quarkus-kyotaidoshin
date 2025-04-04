package com.yaz.kyotaidoshin.core.service.telegram;

import io.micrometer.core.annotation.Timed;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class TelegramUpdateJob {

  private final Set<Long> updateIds = new HashSet<>();

  private final TelegramRestService restService;
  private final TelegramCommandResolver commandResolver;

  @ConfigProperty(name = "app.telegram.get_updates_job")
  boolean getUpdatesJob;

  @Scheduled(every = "3s", concurrentExecution = ConcurrentExecution.SKIP, overdueGracePeriod = "3m")
  public Uni<Void> runAsStart() {

    if (!getUpdatesJob) {
      return Uni.createFrom().voidItem();
    }
    //log.info("TelegramUpdateJob runAsStart");
    final var offset = updateIds.stream().max(Long::compareTo).map(i -> i + 1).orElse(null);
    updateIds.clear();
    return processUpdates(offset)
        .onFailure()
        .invoke(e -> log.error("ERROR TelegramUpdateJob offset: {}", offset, e));
  }

  @Timed(value = "telegram_job.updates_processing", description = "[Telegram Job] A measure of how long it takes to process Telegram updates")
  Uni<Void> processUpdates(Long offset) {
    return restService.getUpdates(offset)
//        .invoke(list -> log.info("Telegram getUpdates: {}", list.size()))
        .toMulti()
        .flatMap(Multi.createFrom()::iterable)
        .filter(up -> updateIds.add(up.updateId()))
        .onItem()
        .transformToUni(commandResolver::resolve)
        .merge()
        .toUni()

        .onFailure(ClientWebApplicationException.class)
        .recoverWithUni(throwable -> {
          final var message = throwable.getMessage();
          if (message.contains("Conflict, status code 409")) {
            //log.warn("Telegram getUpdates conflict: {}", message);
            return Uni.createFrom().voidItem();
          } else {
            log.error("Telegram getUpdates error: {}", message);
            return Uni.createFrom().failure(throwable);
          }
        });
  }

}
