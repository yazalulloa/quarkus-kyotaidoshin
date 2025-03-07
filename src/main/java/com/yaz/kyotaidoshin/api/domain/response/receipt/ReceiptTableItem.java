package com.yaz.kyotaidoshin.api.domain.response.receipt;

import com.yaz.kyotaidoshin.persistence.model.Receipt;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReceiptTableItem(
    String key,
    Receipt item,
    String cardId,
    boolean sentInfoOutOfBounds) {

  public String cardIdRef() {
    return "#" + cardId;
  }

  public String downloadFileName() {
    return "%s_%s_%s.zip".formatted(item.buildingId(), item.month(), item.date());
  }
}
