package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.ApartmentRecord;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.AptDebt;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.AptTotal;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.FormatWithCurrency;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.ReserveFundFormatted;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt.ReserveFundTotal;
import com.yaz.kyotaidoshin.persistence.model.Apartment;
import com.yaz.kyotaidoshin.persistence.model.Building;
import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import com.yaz.kyotaidoshin.persistence.model.domain.ExpenseType;
import com.yaz.kyotaidoshin.persistence.model.domain.ReserveFundType;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.DecimalUtil;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.RxUtil;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestScoped
@RequiredArgsConstructor
public class CalculateReceiptService {

  private final BuildingService buildingService;
  private final ApartmentService apartmentService;
  private final ReserveFundService reserveFundService;
  private final ReceiptService receiptService;
  private final ExpenseService expenseService;
  private final DebtService debtService;
  private final ExtraChargeService extraChargeService;
  private final RateService rateService;
  private final I18NService i18NService;

  private String language;

  public void setLanguage(String language) {
    this.language = language;
  }

  //@CacheResult(cacheName = "calculated_receipt", lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<CalculatedReceipt> calculate(String buildingId, long receiptId) {

    final var receiptSingle = RxUtil.toMaybe(receiptService.read(receiptId))
        .flatMap(Maybe::fromOptional)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Receipt not found")))
        .cache();

    final var buildingSingle = RxUtil.toMaybe(buildingService.read(buildingId))
        .flatMap(Maybe::fromOptional)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Building not found")));

    final var reserveFundsSingle = RxUtil.single(reserveFundService.listByBuilding(buildingId));

    final var apartmentsSingle = RxUtil.single(apartmentService.apartmentsByBuilding(buildingId));

    final var expensesSingle = RxUtil.single(expenseService.readByReceipt(receiptId));

    final var debtsSingle = RxUtil.single(debtService.readByReceipt(buildingId, receiptId));

    final var extraChargesSingle = Single.zip(RxUtil.single(extraChargeService.by(buildingId, buildingId)),
        RxUtil.single(extraChargeService.by(buildingId, String.valueOf(receiptId))),
        (building, receipt) -> {
          return Stream.concat(building.stream(), receipt.stream())
              .toList();
        });

    final var rateSingle = receiptSingle.map(Receipt::rateId)
        .map(rateService::read)
        .flatMap(RxUtil::single)
        .flatMapMaybe(Maybe::fromOptional)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Rate not found")));

    final var calculatedReceiptSingle = Single.zip(receiptSingle, buildingSingle, reserveFundsSingle, apartmentsSingle,
        expensesSingle,
        debtsSingle,
        extraChargesSingle, rateSingle,
        (receipt, building, reserveFundList, apartments, expenseList, debtList, extraChargeList, rate) -> {

          final var expenses = expenseList.stream()
              .filter(expense -> !expense.description().equals("DIFERENCIA DE ALIQUOTA"))
              .filter(expense -> !expense.reserveFund())
              .collect(Collectors.toCollection(LinkedList::new));

          final var totalCommonExpensePair = ConvertUtil.pair(expenses,
              r -> r.type() == ExpenseType.COMMON && !r.reserveFund(), rate.rate());
          final var totalCommonExpensesBeforeReserveFund = totalCommonExpensePair.getLeft();

          final var debtReceiptsAmount = debtList.stream().map(Debt::receipts)
              .reduce(Integer::sum)
              .orElse(0);

          final var debtTotal = debtList.stream().map(Debt::amount)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO);

          final var reserveFundTotals = reserveFundList
              .stream()
              .filter(reserveFund -> reserveFund.active() && DecimalUtil.greaterThanZero(reserveFund.pay()))
              .map(reserveFund -> {

                final var amount = reserveFund.type() == ReserveFundType.FIXED_PAY ? reserveFund.pay() :
                    DecimalUtil.percentageOf(reserveFund.pay(), totalCommonExpensesBeforeReserveFund);

                return ReserveFundTotal.builder()
                    .name(reserveFund.name())
                    .fund(reserveFund.fund())
                    .expense(reserveFund.expense())
                    .amount(amount)
                    .type(reserveFund.type())
                    .expenseType(reserveFund.expenseType())
                    .pay(reserveFund.pay())
                    .addToExpenses(reserveFund.addToExpenses())
                    .build();
              })
              .toList();

          final var totalCommonExpenses = reserveFundTotals.stream()
              .filter(ReserveFundTotal::addToExpenses)
              .filter(res -> res.expenseType() == ExpenseType.COMMON)
              .map(ReserveFundTotal::amount)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO)
              .add(totalCommonExpensesBeforeReserveFund);

          reserveFundTotals.stream().filter(ReserveFundTotal::addToExpenses).map(fund -> {
            final var isFixedPay = fund.type() == ReserveFundType.FIXED_PAY;
            return Expense.builder()
                .description(fund.name() + " " + fund.pay() + (isFixedPay ? "" : "%"))
                .amount(fund.amount())
                .currency(totalCommonExpensePair.getRight())
                .type(fund.expenseType())
                .reserveFund(true)
                .build();
          }).forEach(expenses::add);

          final var aliquotDifference = aliquotDifference(apartments, totalCommonExpenses);

          expenses.add(Expense.builder()
              .description("DIFERENCIA DE ALIQUOTA")
              .amount(aliquotDifference)
              .currency(totalCommonExpensePair.getRight())
              .type(ExpenseType.UNCOMMON)
              .build());

          final var totalUnCommonExpensePair = ConvertUtil.pair(expenses, r -> r.type() == ExpenseType.UNCOMMON,
              rate.rate());

          final var totalUnCommonExpenses = totalUnCommonExpensePair.getLeft();

          final var equalsToZero = DecimalUtil.equalsToZero(totalUnCommonExpenses);
          final var unCommonPay =
              equalsToZero ? BigDecimal.ZERO : totalUnCommonExpenses
                  .divide(BigDecimal.valueOf(apartments.size()), MathContext.DECIMAL128);

          final var buildingExtraCharges = extraChargeList.stream()
              .filter(extraCharge -> extraCharge.parentReference().equals(receipt.buildingId()))
              .toList();

          final var receiptExtraCharges = extraChargeList.stream()
              .filter(extraCharge -> extraCharge.parentReference().equals(String.valueOf(receipt.id())))
              .toList();

          final var aptTotals = apartments.stream()
              .map(apartment -> {

                final var extraCharges = extraCharges(apartment.number(), buildingExtraCharges,
                    receiptExtraCharges);

                final var amounts = totalAptPay(unCommonPay, building, rate.rate(), totalCommonExpenses,
                    apartment.aliquot(), extraCharges)
                    .entrySet()
                    .stream()
                    .map(entry -> new FormatWithCurrency(entry.getKey(), entry.getValue()))
                    .toList();

                return AptTotal.builder()
                    .number(apartment.number())
                    .name(apartment.name())
                    .amounts(amounts)
                    .extraCharges(extraCharges)
                    .build();

              })
              .collect(Collectors.toCollection(LinkedList::new));

          final var apartmentRecords = apartments.stream()
              .map(apartment -> {

                final var aptTotal = aptTotals.stream()
                    .filter(apt -> apt.number().equals(apartment.number()))
                    .findFirst()
                    .orElseThrow();

                return ApartmentRecord.builder()
                    .apartment(apartment)
                    .amounts(aptTotal.amounts())
                    .extraCharges(aptTotal.extraCharges())
                    .build();
              })
              .toList();
          if (language != null) {
            i18NService.setLanguage(language);
          }
          final var debts = apartments.stream()
              .map(apartment -> {
                final var optionalDebt = debtList.stream()
                    .filter(debt -> debt.aptNumber().equals(apartment.number()))
                    .findFirst();

                final var monthStr = optionalDebt.map(Debt::months)
                    .stream()
                    .flatMap(Collection::stream)
                    .map(i18NService::month)
                    .map(String::toUpperCase)
                    .collect(Collectors.joining(", "));

                return AptDebt.builder()
                    .aptNumber(apartment.number())
                    .name(apartment.name())
                    .currency(building.debtCurrency())
                    .amount(optionalDebt.map(Debt::amount).orElse(BigDecimal.ZERO))
                    .receipts(optionalDebt.map(Debt::receipts).orElse(0))
                    .months(optionalDebt.map(Debt::months).orElse(Collections.emptySet()))
                    .monthStr(monthStr.isEmpty() ? "SOLVENTE" : monthStr)
                    .previousPaymentAmount(optionalDebt.map(Debt::previousPaymentAmount).orElse(BigDecimal.ZERO))
                    .previousPaymentAmountCurrency(optionalDebt.map(Debt::previousPaymentAmountCurrency)
                        .orElse("VED"))
                    .build();
              })
              .toList();

          final var total = aptTotals.stream().map(CalculatedReceipt.AptTotal::amounts)
              .flatMap(Collection::stream)
              .filter(m -> m.currency().equals(building.mainCurrency()))
              .map(FormatWithCurrency::amount)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO);

          final var apartmentsTotal = ConvertUtil.numberFormat(building.mainCurrency()).format(total);

          final var thereIsReserveFundExpense = reserveFundTotals.stream()
              .anyMatch(reserveFundTotal -> reserveFundTotal.expense() != null);

          final var reserveFundFormatteds = new ArrayList<ReserveFundFormatted>();

          {
            final var debtTableAdded = new AtomicBoolean(false);
            reserveFundTotals.forEach(fund -> {
              var newFund = fund.fund().add(fund.amount());
              if (fund.expense() != null) {
                newFund = newFund.subtract(fund.expense());
              }

              final var reserveFundCurrency = ConvertUtil.numberFormat("VED");

              final var previousReserveFund = reserveFundCurrency.format(fund.fund());
              final var amountToPay =
                  reserveFundCurrency.format(fund.amount()) + " " + (fund.type() == ReserveFundType.FIXED_PAY ? ""
                      : fund.pay() + "%");
              final var newReserveFund = reserveFundCurrency.format(newFund);

              final var expense = Optional.ofNullable(fund.expense())
                  .map(d -> d.multiply(BigDecimal.valueOf(-1)))
                  .map(reserveFundCurrency::format)
                  .orElse("");

              reserveFundFormatteds.add(ReserveFundFormatted.builder()
                  .name(fund.name())
                  .fundFormatted(previousReserveFund)
                  .expenseFormatted(expense)
                  .amountToPay(amountToPay)
                  .newReserveFund(newReserveFund)
                  .build());

              if ((fund.name().equals("FONDO DE RESERVA") || fund.name().equals("FONDO/RESERVA"))
                  && !debtTableAdded.get()) {
                final var debt = ConvertUtil.numberFormat(building.debtCurrency()).format(debtTotal);

                final var modifiedDebt = ConvertUtil.toCurrency(debtTotal, building.debtCurrency(), rate.rate(), "VED");
                final var fundAfterDebt = reserveFundCurrency.format(newFund.subtract(modifiedDebt));

                reserveFundFormatteds.add(ReserveFundFormatted.builder()
                    .name("P/Cobrar > Recibos  %s".formatted(debtReceiptsAmount))
                    .fundFormatted(debt)
                    .expenseFormatted("")
                    .amountToPay("DEFICIT/Patrimonio")
                    .newReserveFund(fundAfterDebt)
                    .build());

                debtTableAdded.set(true);
              }
            });
          }

          return CalculatedReceipt.builder()
              .id(receipt.id())
              .year(receipt.year())
              .month(Month.of(receipt.month()))
              .monthStr(i18NService.month(receipt.month()).toUpperCase())
              .date(receipt.date())
              .rate(rate)
              .expenses(expenses)
              .totalCommonExpenses(totalCommonExpenses)
              .totalCommonExpensesCurrency(totalCommonExpensePair.getRight())
              .totalUnCommonExpenses(totalUnCommonExpenses)
              .totalUnCommonExpensesCurrency(totalUnCommonExpensePair.getRight())
              .totalDebt(debtTotal)
              .totalDebtFormatted(ConvertUtil.numberFormat(building.debtCurrency()).format(debtTotal))
              .debtReceiptsAmount(debtReceiptsAmount)
              .debts(debts)
              .aptTotals(aptTotals)
              .apartmentsTotal(apartmentsTotal)
              .reserveFundTotals(reserveFundTotals)
              .reserveFundFormatteds(reserveFundFormatteds)
              .thereIsReserveFundExpense(thereIsReserveFundExpense)
              .apartments(apartments)
              .apartmentRecords(apartmentRecords)
              .building(building)
              .emailConfigId(building.emailConfigId())
              .receipt(receipt)
              .build();
        });

    return MutinyUtil.toUni(calculatedReceiptSingle);
  }

  private BigDecimal aliquotDifference(Collection<Apartment> list, BigDecimal totalCommonExpenses) {

    if (DecimalUtil.equalsToZero(totalCommonExpenses)) {
      return BigDecimal.ZERO;
    }

    final var totalAliquot = list.stream()
        .map(Apartment::aliquot)
        .map(aliquot -> DecimalUtil.percentageOf(aliquot, totalCommonExpenses))
        .reduce(BigDecimal::add)
        .orElseThrow(() -> new RuntimeException("NO_ALIQUOT_FOUND"));

    final var aliquoutDifference = totalAliquot.subtract(totalCommonExpenses);

    if (DecimalUtil.greaterThanZero(aliquoutDifference)) {
      return aliquoutDifference;
    }

    return BigDecimal.ZERO;
  }

  private Map<String, BigDecimal> totalAptPay(BigDecimal unCommonPayPerApt, Building building, BigDecimal rate,
      BigDecimal totalCommonExpenses,
      BigDecimal aptAliquot,
      Collection<ExtraCharge> extraCharges) {

    final var currency = building.mainCurrency();
    var preCalculatedPayment = building.fixedPayAmount();

    if (!building.fixedPay()) {
      final var aliquotAmount = DecimalUtil.percentageOf(aptAliquot, totalCommonExpenses);
      // document.add(new Paragraph("MONTO POR ALIQUOTA: " + currencyType.numberFormat().format(aliquotAmount)));
      preCalculatedPayment = aliquotAmount.add(unCommonPayPerApt);//.setScale(2, RoundingMode.UP);
    }

    return totalPayment(building.fixedPay(), preCalculatedPayment, currency, rate, extraCharges);
  }

  private Map<String, BigDecimal> totalPayment(boolean fixedPay,
      BigDecimal preCalculatedPayment,
      String currencyType, BigDecimal usdExchangeRate,
      Collection<ExtraCharge> extraCharges) {


    UnaryOperator<BigDecimal> toUsd = ves -> ves.divide(usdExchangeRate, 2, RoundingMode.HALF_UP);

    UnaryOperator<BigDecimal> toVes = usd -> usd.multiply(usdExchangeRate);

    final var vesExtraCharge = extraCharges.stream().filter(c -> c.currency().equals("VED"))
        .map(ExtraCharge::amount)
        .map(BigDecimal::valueOf)
        .reduce(BigDecimal::add)
        .orElse(BigDecimal.ZERO);

    final var usdExtraCharge = extraCharges.stream().filter(c -> c.currency().equals("USD"))
        .map(ExtraCharge::amount)
        .map(BigDecimal::valueOf)
        .reduce(BigDecimal::add)
        .orElse(BigDecimal.ZERO);


    var usdPay = BigDecimal.ZERO;
    var vesPay = BigDecimal.ZERO;

    vesPay = vesPay.add(vesExtraCharge)
        .add(DecimalUtil.equalsToZero(usdExtraCharge) ? BigDecimal.ZERO : toVes.apply(usdExtraCharge));
    usdPay = usdPay.add(usdExtraCharge)
        .add(DecimalUtil.equalsToZero(vesExtraCharge) ? BigDecimal.ZERO : toUsd.apply(vesExtraCharge));

    if (fixedPay) {
      if (currencyType.equals("USD")) {
        usdPay = usdPay.add(preCalculatedPayment);
        vesPay = vesPay.add(toVes.apply(preCalculatedPayment));
      } else {
        vesPay = vesPay.add(preCalculatedPayment);
        usdPay = usdPay.add(toUsd.apply(preCalculatedPayment));
      }
    } else {
      vesPay = vesPay.add(preCalculatedPayment);
      usdPay = usdPay.add(toUsd.apply(preCalculatedPayment));
    }

    UnaryOperator<BigDecimal> function = bigDecimal -> {
            /*if (building.roundUpPayments()) {
                return bigDecimal.setScale(0, RoundingMode.UP);
            }*/

      return bigDecimal.setScale(2, RoundingMode.HALF_UP);
    };

    return Map.of(
        "USD", function.apply(usdPay),
        "VED", function.apply(vesPay)
    );
  }

  private List<ExtraCharge> extraCharges(String aptNumber, List<ExtraCharge> first, List<ExtraCharge> second) {

    final var receiptCharges = Optional.ofNullable(first)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(extraCharge -> extraCharge.apartments().stream().anyMatch(apt -> apt.number().equals(aptNumber)))
        .filter(extraCharge -> DecimalUtil.greaterThanZero(BigDecimal.valueOf(extraCharge.amount())));

    final var buildingCharges = Optional.ofNullable(second)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(extraCharge -> extraCharge.apartments().stream().anyMatch(apt -> apt.number().equals(aptNumber)))
        .filter(extraCharge -> DecimalUtil.greaterThanZero(BigDecimal.valueOf(extraCharge.amount())));

    return Stream.concat(receiptCharges, buildingCharges).toList();
  }
}
