package com.yaz.kyotaidoshin.api.domain.response.debt;

import lombok.Builder;

@Builder(toBuilder = true)
public record DebtFormResponse(
    String generalFieldError,
    DebtTableItem tableItem,
    DebtCountersDto counters
) {

  public boolean isSuccess() {
    return generalFieldError == null;
  }

}
