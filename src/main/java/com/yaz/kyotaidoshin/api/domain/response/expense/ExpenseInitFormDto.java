package com.yaz.kyotaidoshin.api.domain.response.expense;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record ExpenseInitFormDto(
    String key,
    long expensesCount,

    String totalUnCommonExpenses,
    String totalCommonExpenses,
    String totalUnCommonExpensesPlusReserveFunds,
    String totalCommonExpensesPlusReserveFunds,
    List<ExpenseTableItem> expenses

) {

}
