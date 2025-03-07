package com.yaz.kyotaidoshin.api.domain.response.funds;

import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseTableItem;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReserveFundFormResponse(

    String nameFieldError,
    String fundFieldError,
    String expenseFieldError,
    String payFieldError,
    String generalFieldError,

    ReserveFundTableItem tableItem,

    ReserveFundCountersDto counters,
    ExpenseCountersDto expenseCountersDto,

    ExpenseTableItem expenseTableItem
) {

  public boolean isSuccess() {
    return nameFieldError == null && fundFieldError == null && expenseFieldError == null && payFieldError == null
        && generalFieldError == null;
  }

}
