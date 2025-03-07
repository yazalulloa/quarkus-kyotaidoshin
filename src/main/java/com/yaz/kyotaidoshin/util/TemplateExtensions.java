package com.yaz.kyotaidoshin.util;

import com.yaz.kyotaidoshin.api.domain.response.debt.DebtTableItem;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.AptDebt;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.ReserveFundTotal;
import com.yaz.kyotaidoshin.persistence.model.Building;
import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import com.yaz.kyotaidoshin.persistence.model.domain.ReserveFundType;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.Json;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Add your custom Qute extension methods here.
 */
@Slf4j
@TemplateExtension
public class TemplateExtensions {

  /**
   * This registers the String.capitalise extension method
   */
  public static String capitalise(String string) {
    StringBuilder sb = new StringBuilder();
    for (String part : string.split("\\s+")) {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      if (!part.isEmpty()) {
        sb.append(part.substring(0, 1).toUpperCase());
        sb.append(part.substring(1));
      }
    }
    return sb.toString();
  }

  static String fromEpoch(Long epoch) {
    if (epoch == null) {
      return "";
    }

    final var dateTime = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC);
    return DateUtil.formatVe(dateTime);
  }

  static String fromEpochMillis(Long epoch) {
    if (epoch == null) {
      return "";
    }

    final var dateTime = Instant.ofEpochMilli(epoch).atZone(ZoneOffset.UTC);
    return DateUtil.formatVe(dateTime);
  }

  static String formatDate(LocalDateTime dateTime) {
    if (dateTime == null) {
      return "";
    }

    return DateUtil.formatVe(dateTime.atZone(ZoneOffset.UTC));
  }

  static String formatFileSize(Long fileSize) {
    if (fileSize == null) {
      return "";
    }

    return FileUtil.byteCountToDisplaySize(fileSize);
  }

  static String formatCreatedAt(Rate rate) {
    return Optional.ofNullable(rate.createdAt())
        .map(dateTime -> dateTime.atZone(ZoneOffset.UTC))
        .map(DateUtil::formatVe)
        .orElse("");
  }

  static Double toDouble(BigDecimal val) {
    return val.doubleValue();
  }


  static String formatAmount(ExtraCharge extraCharge) {

    return ConvertUtil.numberFormat(extraCharge.currency()).format(extraCharge.amount());
  }

  static String formatTotal(ExtraCharge extraCharge) {
    return ConvertUtil.numberFormat(extraCharge.currency())
        .format(extraCharge.amount() * extraCharge.apartments().size());
  }

  static String formatAmount(Expense expense) {
    return ConvertUtil.numberFormat(expense.currency()).format(expense.amount());
  }

  static String formatPreviousPaymentAmount(Debt debt) {
    if (debt.previousPaymentAmount() == null || debt.previousPaymentAmountCurrency() == null
        || DecimalUtil.equalsToZero(debt.previousPaymentAmount())) {
      return "";
    }

    return ConvertUtil.numberFormat(debt.previousPaymentAmountCurrency()).format(debt.previousPaymentAmount());
  }

  static String formatAmount(DebtTableItem item) {

    if (item.currency() == null || item.item() == null || item.item().amount() == null) {
      return "";
    }

    return ConvertUtil.numberFormat(item.currency()).format(item.item().amount());
  }

  public static String formatJsonObj(Object editAttr) {

    final var json = Json.encode(editAttr);

    return Base64.getUrlEncoder().encodeToString(json.getBytes());
  }

  public static String formatRate(Rate rate) {
    return formatWithCurrency(rate.toCurrency(), rate.rate());
  }

  public static String format(CalculatedReceipt.FormatWithCurrency formatWithCurrency) {
    return formatWithCurrency(formatWithCurrency.currency(), formatWithCurrency.amount());
  }

  public static String formatWithCurrency(String currency, BigDecimal amount) {
    if (!TemplateGlobals.ALLOWED_CURRENCIES.contains(currency)) {
      throw new IllegalArgumentException("Currency not allowed: " + currency);
    }
    return ConvertUtil.numberFormat(currency).format(amount);
  }

  public static boolean showMultipleCurrenciesAmountToPay(Building building) {
    return building.currenciesToShowAmountToPay().size() > 1;
  }

  public static String formatAmount(AptDebt debt) {
    return ConvertUtil.numberFormat(debt.currency()).format(debt.amount());
  }

  public static String formatPreviousPaymentAmount(AptDebt debt) {
    return ConvertUtil.numberFormat(debt.previousPaymentAmountCurrency()).format(debt.previousPaymentAmount());
  }

  public static String formatFund(ReserveFundTotal reserveFund) {
    return ConvertUtil.numberFormat("VED").format(reserveFund.fund());
  }

  public static String formatExpense(ReserveFundTotal fund) {
    return Optional.ofNullable(fund.expense())
        .map(d -> d.multiply(BigDecimal.valueOf(-1)))
        .map(ConvertUtil.numberFormat("VED")::format)
        .orElse("");
  }

  public static String amountToPay(ReserveFundTotal fund) {

    return ConvertUtil.numberFormat("VED").format(fund.amount()) + " " + (fund.type() == ReserveFundType.FIXED_PAY ? ""
        : fund.pay() + "%");
  }

  public static String formatNewFund(ReserveFundTotal fund) {
    var newFund = fund.fund().add(fund.amount());
    if (fund.expense() != null) {
      newFund = newFund.subtract(fund.expense());
    }
    return ConvertUtil.numberFormat("VED").format(newFund);
  }

  public static String logo(IdentityProvider provider) {
    return "/assets/images/signin-%s.svg".formatted(provider.name().toLowerCase());
  }
}
