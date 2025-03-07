package com.yaz.kyotaidoshin.persistence.domain;

import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import java.util.List;
import lombok.Builder;

@Builder
public record ReceiptCreateRequest(
    Receipt receipt,
    List<Expense> expenses,
    List<ExtraCharge> extraCharges,
    List<Debt> debts
) {

}
