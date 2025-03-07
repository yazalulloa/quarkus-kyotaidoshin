package com.yaz.kyotaidoshin.api.domain.response.expense;


import com.yaz.kyotaidoshin.persistence.model.Expense;
import lombok.Builder;

@Builder(toBuilder = true)
public record ExpenseTableItem(
    String key,
    Expense item,
    String cardId,
    boolean outOfBoundsUpdate,
    boolean addAfterEnd) {

  public String cardIdRef() {
    return "#" + cardId();
  }
}
