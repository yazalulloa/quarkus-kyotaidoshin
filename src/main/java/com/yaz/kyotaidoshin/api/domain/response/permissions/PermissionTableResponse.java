package com.yaz.kyotaidoshin.api.domain.response.permissions;

import com.yaz.kyotaidoshin.persistence.model.Permission;
import java.util.List;
import lombok.Builder;

@Builder
public record PermissionTableResponse(
    List<Item> results,
    String lastKey,
    PermissionCountersDto counters
) {

  @Builder(toBuilder = true)
  public record Item(
      String key,
      Permission item,
      boolean outOfBoundUpdate,
      String cardId
  ) {

    public String cardIdRef() {
      return "#" + cardId();
    }

  }
}

