package com.yaz.kyotaidoshin.api.domain.response.rate;

import com.yaz.kyotaidoshin.persistence.model.Rate;
import java.util.Collection;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record RateTableResponse(
    Long selected,
    String lastKey,
    List<Item> results,
    RateCountersDto countersDto,

    String date,
    Collection<String> currencies) {


  @Builder(toBuilder = true)
  public record Item(
      String key,
      Rate item,
      String cardId,
      boolean isUpdate
  ) {

    public String cardIdRef() {
      return "#" + cardId();
    }

  }
}
