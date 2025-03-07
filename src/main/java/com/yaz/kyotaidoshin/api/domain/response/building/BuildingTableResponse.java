package com.yaz.kyotaidoshin.api.domain.response.building;

import com.yaz.kyotaidoshin.persistence.model.Building;
import java.util.Collection;
import lombok.Builder;


@Builder(toBuilder = true)
public record BuildingTableResponse(
    long totalCount,
    String lastKey,
    Collection<Item> results) {

  @Builder(toBuilder = true)
  public record Item(
      String key,
      Building item,
      boolean outOfBoundUpdate,
      String cardId
  ) {

    public String cardIdRef() {
      return "#" + cardId();
    }

  }
}
