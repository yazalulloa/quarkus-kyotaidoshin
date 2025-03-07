package com.yaz.kyotaidoshin.api.domain.response.debt;

import com.yaz.kyotaidoshin.persistence.model.Debt;
import lombok.Builder;

@Builder
public record DebtTableItem(
    String key,
    Debt item,
    String currency,
    String cardId,
    boolean outOfBoundsUpdate,
    boolean addAfterEnd) {

  public String cardIdRef() {
    return "#" + cardId();
  }
}
