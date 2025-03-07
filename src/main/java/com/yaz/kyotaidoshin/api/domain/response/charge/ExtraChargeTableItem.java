package com.yaz.kyotaidoshin.api.domain.response.charge;

import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import lombok.Builder;

@Builder
public record ExtraChargeTableItem(
    String key,
    ExtraCharge item,
    String cardId,
    boolean outOfBoundsUpdate,
    boolean addAfterEnd) {

  public String cardIdRef() {
    return "#" + cardId();
  }
}
