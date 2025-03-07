package com.yaz.kyotaidoshin.api.domain.response;

import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.model.User;
import java.util.Collection;
import java.util.List;
import lombok.Builder;


@Builder
public record UserTableResponse(
    long totalCount,
    String lastKey,
    Collection<Item> results) {

  @Builder
  public record Item(
      String key,
      User user,
      String cardId,
      List<NotificationKey> notificationKeys) {

    public String cardIdRef() {
      return "#" + cardId();
    }
  }

  public record NotificationKey(
      String key,
      NotificationEvent.Event event

  ) {

  }
}
