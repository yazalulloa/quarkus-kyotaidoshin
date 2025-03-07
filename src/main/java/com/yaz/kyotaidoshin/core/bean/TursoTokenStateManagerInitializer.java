package com.yaz.kyotaidoshin.core.bean;

import com.yaz.kyotaidoshin.core.service.OidcDbTokenService;
import com.yaz.kyotaidoshin.util.DateUtil;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import jakarta.enterprise.event.Observes;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TursoTokenStateManagerInitializer {


  private static volatile Long timerId = null;

  private static void periodicallyDeleteExpiredTokens(Vertx vertx, OidcDbTokenService service,
      long delayBetweenChecks) {
    timerId = vertx
        .setPeriodic(10000, delayBetweenChecks, new Handler<>() {

          private final AtomicBoolean deleteInProgress = new AtomicBoolean(false);

          @Override
          public void handle(Long aLong) {
            if (deleteInProgress.compareAndSet(false, true)) {

              final var instant = Instant.now().minus(90, ChronoUnit.DAYS);
//              log.info("Delete expired OIDC token states from database {}", DateUtil.format(instant));
              service.deleteIfExpired(instant.getEpochSecond())
                  .subscribe()
                  .with(
                      deleted -> {
                        log.info("Expired OIDC token states from database {}", deleted);
                        // success
                        deleteInProgress.set(false);
                      },
                      t -> {
                        log.error("Failed to expired OIDC token states from database", t);
                        deleteInProgress.set(false);
                      });
            }
          }
        });
  }

  void initialize(@Observes StartupEvent event, Vertx vertx, OidcDbTokenService service) {
    periodicallyDeleteExpiredTokens(vertx, service, Duration.ofHours(1).toMillis());
  }

  void shutdown(@Observes ShutdownEvent event, Vertx vertx) {
    if (timerId != null) {
      vertx.cancelTimer(timerId);
    }
  }
}
