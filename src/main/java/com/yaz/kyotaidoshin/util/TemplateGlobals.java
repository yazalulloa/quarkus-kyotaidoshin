package com.yaz.kyotaidoshin.util;

import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.model.domain.ExpenseType;
import com.yaz.kyotaidoshin.persistence.model.domain.ReserveFundType;
import io.quarkus.qute.TemplateGlobal;
import java.util.Collection;
import java.util.List;

@TemplateGlobal
public class TemplateGlobals {

  //  public static Currency[] GLO_CURRENCIES = Currency.VALUES;
  public static Collection<String> ALLOWED_CURRENCIES = List.of("USD", "VED");
  public static NotificationEvent.Event[] GLO_NOTIFICATION_EVENTS = NotificationEvent.Event.VALUES;

  public static ReserveFundType[] GLO_RESERVE_FUND_TYPES = ReserveFundType.VALUES;

  public static ExpenseType[] GLO_EXPENSE_TYPES = ExpenseType.VALUES;
  public static String[] GLO_ALL_PERMS = PermissionUtil.ALL_PERMS;
  public static PermissionUtil.Type[] GLO_ALL_PERM_TYPES = PermissionUtil.ALL_TYPES;


}
