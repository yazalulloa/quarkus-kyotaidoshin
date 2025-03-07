package com.yaz.kyotaidoshin.core.service.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import java.util.List;
import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record ReceiptRecord(
    Receipt receipt,
    List<Expense> expenses,
    List<ExtraCharge> extraCharges,
    List<Debt> debts
) {

}
