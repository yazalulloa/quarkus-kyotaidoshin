package com.yaz.kyotaidoshin.api.domain.response.receipt;

import com.yaz.kyotaidoshin.api.domain.response.rate.RateTableResponse;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReceiptFormDto(
    String key,
    int month,
    int year,
    int[] years,
    String buildingName,
    String date,
    RateTableResponse rates,
    String generalError
) {

  public boolean isSuccess() {
    return generalError == null;
  }

}
