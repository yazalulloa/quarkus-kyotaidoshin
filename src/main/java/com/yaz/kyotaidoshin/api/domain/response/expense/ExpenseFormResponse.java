package com.yaz.kyotaidoshin.api.domain.response.expense;

import lombok.Builder;

@Builder(toBuilder = true)
public record ExpenseFormResponse(
    String descriptionFieldError,
    String amountFieldError,
    String generalFieldError,

    ExpenseTableItem tableItem,
    ExpenseCountersDto counters
) {

  public boolean isSuccess() {
    return descriptionFieldError == null && amountFieldError == null && generalFieldError == null;
  }

}
