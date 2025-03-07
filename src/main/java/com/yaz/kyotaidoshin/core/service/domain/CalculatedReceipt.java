package com.yaz.kyotaidoshin.core.service.domain;

import com.yaz.kyotaidoshin.persistence.model.Apartment;
import com.yaz.kyotaidoshin.persistence.model.Building;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import com.yaz.kyotaidoshin.persistence.model.domain.ExpenseType;
import com.yaz.kyotaidoshin.persistence.model.domain.ReserveFundType;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;
import lombok.Builder;

@Builder(toBuilder = true)
public record CalculatedReceipt(
    long id,
    int year,
    Month month,
    String monthStr,
    LocalDate date,
    List<Expense> expenses,
    BigDecimal totalCommonExpenses,
    String totalCommonExpensesCurrency,
    BigDecimal totalUnCommonExpenses,
    String totalUnCommonExpensesCurrency,
    List<AptDebt> debts,
    List<AptTotal> aptTotals,
    String apartmentsTotal,
    BigDecimal totalDebt,
    String totalDebtFormatted,
    Integer debtReceiptsAmount,
    List<ExtraCharge> extraCharges,
    Rate rate,
    List<ReserveFundTotal> reserveFundTotals,
    List<ReserveFundFormatted> reserveFundFormatteds,
    boolean thereIsReserveFundExpense,
    Building building,
    List<Apartment> apartments,
    List<ApartmentRecord> apartmentRecords,
    String emailConfigId,
    Receipt receipt,
    String clientId,

    String key
) {

  public String formatTotalCommonExpenses() {
    return ConvertUtil.numberFormat(totalCommonExpensesCurrency).format(totalCommonExpenses);
  }

  public String formatTotalUnCommonExpenses() {
    return ConvertUtil.numberFormat(totalUnCommonExpensesCurrency).format(totalUnCommonExpenses);
  }

  @Builder(toBuilder = true)
  public record ApartmentRecord(
      Apartment apartment,
      List<FormatWithCurrency> amounts,
      List<ExtraCharge> extraCharges
  ) {

  }

  @Builder(toBuilder = true)
  public record AptTotal(
      String number,
      String name,
      List<FormatWithCurrency> amounts,
      List<ExtraCharge> extraCharges
  ) {

  }

  @Builder(toBuilder = true)
  public record FormatWithCurrency(String currency, BigDecimal amount) {

  }

  @Builder(toBuilder = true)
  public record ReserveFundTotal(
      String name,
      BigDecimal fund,
      BigDecimal expense,
      BigDecimal amount,
      ReserveFundType type,
      ExpenseType expenseType,
      BigDecimal pay,
      Boolean addToExpenses
  ) {

  }

  @Builder(toBuilder = true)
  public record ReserveFundFormatted(
      String name,
      String fundFormatted,
      String expenseFormatted,
      String amountToPay,
      String newReserveFund
  ) {

  }

  @Builder(toBuilder = true)
  public record AptDebt(
      String aptNumber,
      String name,

      int receipts,
      String currency,

      BigDecimal amount,
      String monthStr,

      Set<Integer> months,

      BigDecimal previousPaymentAmount,

      String previousPaymentAmountCurrency
  ) {

  }
}
