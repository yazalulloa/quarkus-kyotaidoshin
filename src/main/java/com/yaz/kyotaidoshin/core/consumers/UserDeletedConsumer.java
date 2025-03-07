package com.yaz.kyotaidoshin.core.consumers;

import com.yaz.kyotaidoshin.core.domain.events.UserDeletedEvent;
import com.yaz.kyotaidoshin.core.service.NotificationEventService;
import com.yaz.kyotaidoshin.core.service.OidcDbTokenService;
import com.yaz.kyotaidoshin.core.service.PermissionService;
import com.yaz.kyotaidoshin.core.service.TelegramChatService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class UserDeletedConsumer {

  private final OidcDbTokenService tokenService;
  private final NotificationEventService notificationEventService;
  private final PermissionService permissionService;
  private final TelegramChatService telegramChatService;

  public void userDeleted(@ObservesAsync UserDeletedEvent event) {
    final var userId = event.id();
    log.debug("userDeleted: {}", event);

    Uni.join()
        .all(tokenService.deleteByUser(userId),
            notificationEventService.deleteByUser(userId),
            permissionService.deleteByUser(userId),
            telegramChatService.deleteByUser(userId)
        )
        .andCollectFailures()
        .subscribe()
        .with(
            i -> {
              log.debug("Deleting user data: {} deleted: {}", event.id(), i);
            },
            e -> {
              log.error("ERROR deleting user data: {}", event.id(), e);
            });

  }


}
