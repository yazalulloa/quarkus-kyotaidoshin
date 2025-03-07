package com.yaz.kyotaidoshin.api.domain.response.debt;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record DebtInitFormDto(
    String key,
    int debtReceiptsTotal,
    String debtTotal,
    List<DebtTableItem> debts
) {

}
