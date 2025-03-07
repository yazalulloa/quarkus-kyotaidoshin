package com.yaz.kyotaidoshin.api.domain.response.funds;

import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseCountersDto;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReserveFundCountersDto(
    long count,
    ExpenseCountersDto expenseCountersDto

) {

  public static ReserveFundCountersDto count(long count) {
    return ReserveFundCountersDto.builder()
        .count(count)
        .build();
  }

}
