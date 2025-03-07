package com.yaz.kyotaidoshin.api.domain.response.apartments;

import com.yaz.kyotaidoshin.persistence.model.Apartment;
import java.util.List;
import java.util.Set;
import lombok.Builder;

@Builder
public record ApartmentTableResponse(
    List<Item> results,
    String lastKey,
    ApartmentCountersDto countersDto,
    String q,
    Set<String> buildings) {

  @Builder(toBuilder = true)
  public record Item(
      String key,
      Apartment item,
      boolean outOfBoundUpdate,
      String cardId
  ) {

    public String cardIdRef() {
      return "#" + cardId();
    }

  }
}
