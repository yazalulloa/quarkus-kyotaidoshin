package com.yaz.kyotaidoshin.api.domain.response.receipt;

import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.debt.DebtInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundInitFormDto;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReceiptInitFormDto(

    ReceiptFormDto receiptForm,

    List<ExtraCharge.Apt> apts,

    ExpenseInitFormDto expenseDto,

    ExtraChargeInitFormDto extraChargeDto,

    ReserveFundInitFormDto reserveFundDto,
    DebtInitFormDto debtDto
) {

}
