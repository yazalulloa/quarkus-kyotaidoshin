package com.yaz.kyotaidoshin.api.domain.response.building;

import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundInitFormDto;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record BuildingInitFormDto(
    boolean isEdit,
    String key,
    List<EmailConfig> emailConfigs,

    String id,
    String name,
    String rif,
    String mainCurrency,
    String debtCurrency,
    String currenciesToShowAmountToPay,
    boolean fixedPay,
    BigDecimal fixedPayAmount,
    boolean roundUpPayments,
    String emailConfigId,

    List<ExtraCharge.Apt> apts,

    ExtraChargeInitFormDto extraChargeDto,

    ReserveFundInitFormDto reserveFundDto
) {

  @Builder(toBuilder = true)
  public record EmailConfig(
      String id,
      String key,
      String email
  ) {

  }
}
