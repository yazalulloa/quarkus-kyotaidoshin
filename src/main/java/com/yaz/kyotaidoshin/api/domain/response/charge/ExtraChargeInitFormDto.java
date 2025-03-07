package com.yaz.kyotaidoshin.api.domain.response.charge;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record ExtraChargeInitFormDto(
    String key,
    String total,
    List<ExtraChargeTableItem> extraCharges
) {

}
