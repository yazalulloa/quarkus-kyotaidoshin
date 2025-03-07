package com.yaz.kyotaidoshin.api.domain.response.funds;

import com.yaz.kyotaidoshin.persistence.model.ReserveFund;
import lombok.Builder;

@Builder
public record ReserveFundTableItem(
    String key,
    ReserveFund item,
    String cardId,
    boolean outOfBoundsUpdate,
    boolean addAfterEnd) {

  public String cardIdRef() {
    return "#" + cardId();
  }
}
