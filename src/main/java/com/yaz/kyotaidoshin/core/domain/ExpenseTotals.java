package com.yaz.kyotaidoshin.core.domain;

import com.yaz.kyotaidoshin.util.ConvertUtil;
import java.math.BigDecimal;
import lombok.Builder;

@Builder

public record ExpenseTotals(
    Total unCommon,
    Total common
) {

  public String formatCommon() {
    return ConvertUtil.numberFormat(common.currency).format(common.amount);
  }

  public String formatUnCommon() {
    return ConvertUtil.numberFormat(unCommon.currency).format(unCommon.amount);
  }

  public record Total(
      BigDecimal amount,
      String currency
  ) {

  }

}
