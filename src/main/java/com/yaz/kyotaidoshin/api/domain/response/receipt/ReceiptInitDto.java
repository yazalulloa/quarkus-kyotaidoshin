package com.yaz.kyotaidoshin.api.domain.response.receipt;

import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReceiptInitDto(

    ReceiptTableResponse table,
    List<String> buildings,
    List<Apts> apts
) {

  @Builder(toBuilder = true)
  public record Apts(
      String building,
      List<ExtraCharge.Apt> apts
  ) {

  }
}
