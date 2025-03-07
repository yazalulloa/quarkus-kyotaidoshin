package com.yaz.kyotaidoshin.util;

import com.yaz.kyotaidoshin.core.service.NotificationService;
import com.yaz.kyotaidoshin.core.service.PermissionFixer;
import io.quarkus.oidc.runtime.TenantConfigBean;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.json.Json;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class Startup {

  private final TenantConfigBean tenantConfigBean;
  private final NotificationService notificationService;
  private final EnvParams envParams;
  private final PermissionFixer permissionFixer;

  @ConfigProperty(name = "app.fix-permissions", defaultValue = "false")
  boolean fixPermissions;

  @io.quarkus.runtime.Startup(value = 0)
  void init() {
    envParams.saveAppStartedAt();
    if (envParams.isShowDir()) {
      Single.fromCallable(FileUtil::showDir)
          .doOnSuccess(str -> log.info("\n{}", str))
          .ignoreElement()
          .subscribeOn(Schedulers.io())
          .subscribe(() -> {
          }, t -> log.error("Error showing dir", t));
    }
  }

  /**
   * This method is executed at the start of your application
   */
  public void start(@Observes StartupEvent evt) {

    final var tenants = tenantConfigBean.getStaticTenantsConfig().keySet();
    log.info("Tenants: {}", tenants);

//    tenantConfigBean.getStaticTenantsConfig().forEach((k, v) -> {
//      log.info("TenantConfig: {} -> {}", k, Json.encode(v.oidcConfig()));
//    });

    final var profiles = ConfigUtils.getProfiles();
    log.info("Profiles: {}", profiles);
    final var cloudProvider = System.getenv("CLOUD_PROVIDER");
    log.info("Cloud provider: {}", cloudProvider);
    log.info("Cores: {}", CpuCoreSensor.availableProcessors());
    log.info("Event Loop Size {}", VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
  }

  @io.quarkus.runtime.Startup(value = Integer.MAX_VALUE)
  void notifyStartup() {

    if (LaunchMode.current() == LaunchMode.NORMAL) {
      notificationService.sendAppStartup();
    }
    log.info("The application is starting...");

    if (fixPermissions) {

      permissionFixer.all()
          .subscribe()
          .with(
              v -> log.info("Permissions fixed"),
              t -> log.error("Error fixing permissions", t)
          );
    }
  }

  @Shutdown(value = 0)
  void shutdown() {
    if (LaunchMode.current() == LaunchMode.NORMAL) {
      try {
        notificationService.sendShuttingDownApp()
            .blockingAwait(3, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("Error sending shutting down message", e);
      }
    }
  }

  void shutdown(@Observes ShutdownEvent event) {
    log.info("The application is shutting down");
  }
}
