package com.yaz.kyotaidoshin.persistence.model.domain;

import io.quarkus.qute.TemplateEnum;

@TemplateEnum
public enum ExpenseType {
  COMMON, UNCOMMON;

  public static final ExpenseType[] VALUES = values();
}
