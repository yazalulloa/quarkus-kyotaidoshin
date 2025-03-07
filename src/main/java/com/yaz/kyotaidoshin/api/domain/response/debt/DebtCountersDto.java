package com.yaz.kyotaidoshin.api.domain.response.debt;

import lombok.Builder;

@Builder
public record DebtCountersDto(
    long count,
    int receipts,
    String total
) {

}
