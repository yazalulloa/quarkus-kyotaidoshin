package com.yaz.kyotaidoshin.core.service.csv;

import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge.Type;
import com.yaz.kyotaidoshin.persistence.model.domain.ExpenseType;
import com.yaz.kyotaidoshin.util.DecimalUtil;
import com.yaz.kyotaidoshin.util.PoiUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@LookupIfProperty(name = "app.poi.parsing", stringValue = "streaming")
//@Named("streaming")
@ApplicationScoped
public class ReceiptParserStreaming extends ReceiptParserAbstractImpl {

  private final Vertx vertx;


  //  @Inject
  public ReceiptParserStreaming(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected Vertx vertx() {
    return vertx;
  }

  @Override
  public Single<CsvReceipt> parse(Path path) {

    return Single.fromSupplier(() -> {
          final var expenses = new ArrayList<Expense>();
          final var debts = new ArrayList<Debt>();
          final var extraCharges = new ArrayList<ExtraCharge>();

          final var poiProcessor = new PoiProcessor(path, 0, null, poiPage -> {

            switch (poiPage.sheetIndex()) {
              case 0:
                expenses.addAll(expenses(poiPage));
                break;
              case 1:
                debts.addAll(debts(poiPage));
                break;
              case 2, 4:
                extraCharges.addAll(extraCharges(poiPage));
                break;
              default:
                log.info("Sheet index not supported: {}", poiPage.sheetIndex());
                break;
            }

          });
          poiProcessor.streamFile();

          return CsvReceipt.builder()
              .expenses(expenses)
              .debts(debts)
              .extraCharges(extraCharges)
              .build();
        })
        .subscribeOn(Schedulers.io());

  }

  private List<Expense> expenses(PoiPage page) {
    var type = ExpenseType.COMMON;
    final var expenses = new ArrayList<Expense>();

    for (Row row : page.rows()) {

      final var cells = row.cells();
      if (type == ExpenseType.UNCOMMON && cells.isEmpty()) {
        break;
      }

      if (!cells.isEmpty()) {
        final var values = cells.values();
        final var iterator = values.iterator();
        final var description = iterator.next().value().replaceAll("\\s{2,}", " ").trim();
        if (description.contains("GASTOS NO COMUNES") || description.contains("TOTAL GASTOS COMUNES")) {
          type = ExpenseType.UNCOMMON;
          continue;
        }

        if (description.contains("FONDOS DE") || description.contains("TOTAL GASTOS DEL MES")) {
          break;
        }

        if (cells.size() >= 2) {

          final var amount = iterator.next().value().trim();
          if (!PoiUtil.isThereIsANumber(amount)) {
            continue;
          }

          expenses.add(Expense.builder()
              .description(description)
              .amount(PoiUtil.decimal(amount))
              .type(type)
              .currency("VED")
              .build());
        }
      }
    }

    return expenses;
  }

  private List<Debt> debts(PoiPage page) {
    final var debts = new ArrayList<Debt>();

    for (Row row : page.rows()) {

      if (row.cells().size() >= 4) {
        final var iterator = row.cells().values().iterator();
        final var apt = iterator.next().value().trim();

        final var name = iterator.next().value().trim();
        final var receipts = iterator.next().value().replace("CUOTA", "").trim();
        final var amount = iterator.next().value().trim().replace(",", "");

        if (!amount.isEmpty() && !StringUtil.isNumeric(amount)) {
          continue;
        }

        final var status = iterator.hasNext() ? iterator.next().value().trim() : "";
        var abono = iterator.hasNext() ? iterator.next().value().trim() : null;

        if (abono == null && StringUtil.hasNumbers(status)) {
          abono = status;
        }

        final var previousPaymentAmount = Optional.ofNullable(abono)
            .map(str -> str.replace("$", "").replace("Bs.", "").trim())
            .filter(str -> !str.equals("OJO") && !str.equals("ABONO") && !str.isEmpty() && !str.contains("MESES"))
            .map(str -> {
              try {
                return PoiUtil.decimal(str);
              } catch (Exception e) {
                return null;
              }
            })
            .orElse(BigDecimal.ZERO);

        final var previousPaymentCurrency = Optional.ofNullable(abono)
            .filter(str -> str.contains("$"))
            .map(b -> "USD")
            .orElse("VED");

        final var amountDecimal = amount.isEmpty() ? BigDecimal.ZERO : PoiUtil.decimal(amount);

        final var debt = Debt.builder()
            .aptNumber(apt)
            //.name(name)
            .receipts(PoiUtil.decimal(receipts).intValue())
            .amount(amountDecimal)
            .months(months(status))
            .previousPaymentAmount(previousPaymentAmount)
            .previousPaymentAmountCurrency(previousPaymentCurrency)
            .build();

        debts.add(debt);
      }
    }

    return debts;
  }


  private List<ExtraCharge> extraCharges(PoiPage page) {
    final var list = new ArrayList<ExtraChargeKey>();

    final var afterDesc = new AtomicBoolean(false);
    for (Row initRow : page.rows()) {
      final var cellIterator = initRow.cells().values().iterator();

      while (cellIterator.hasNext()) {
        final var initCell = cellIterator.next();
        final var cellValue = initCell.value();
        final var cellColumn = initCell.column();

        if (cellValue.equals("APTO") || cellValue.equals("MONTO")) {
          afterDesc.set(true);
          break;
        }

        if (!afterDesc.get()) {
          list.stream().filter(key -> key.columnAddr.equals(cellColumn))
              .findFirst()
              .ifPresent(extraChargeKey -> {

                if (afterDesc.get()) {
                  extraChargeKey.getApartments().add(cellValue);
                  //extraChargeKey.addExtraCharge(cellValue, null);
                } else {
                  list.remove(extraChargeKey);
                }

              });

          if (!afterDesc.get()) {
            list.add(ExtraChargeKey.builder()
                .columnAddr(cellColumn)
                .description(cellValue)
                .build());
          }
        } else {
          list.stream().filter(key -> key.columnAddr.equals(cellColumn))
              .findFirst()
              .ifPresent(extraChargeKey -> {

                if (!cellIterator.hasNext()) {
                  return;
                }

                final var amountStr = cellIterator.next().value().trim().replace(",", "");

                if (!StringUtil.isNumeric(amountStr)) {
                  return;
                }

                final var amount = DecimalUtil.decimal(amountStr);
                if (extraChargeKey.getAmount() != null && !DecimalUtil.equalsTo(extraChargeKey.getAmount(), amount)) {
                  final var first = list.stream().filter(
                          key -> key.columnAddr.equals(cellColumn) && key.amount != null && DecimalUtil.equalsTo(
                              key.amount, amount))
                      .findFirst();

                  if (first.isPresent()) {
                    first.get().apartments.add(cellValue);
                  } else {
                    final var chargeKey = ExtraChargeKey.builder()
                        .columnAddr(cellColumn)
                        .description(extraChargeKey.description)
                        .amount(amount)
                        .build();

                    chargeKey.apartments.add(cellValue);
                    list.add(chargeKey);
                  }

                } else {
                  extraChargeKey.setAmount(amount);
                  extraChargeKey.getApartments().add(cellValue);
                }

              });
        }

      }

    }

    return list.stream()
        .filter(key -> key.amount != null && !key.apartments.isEmpty())
        .map(key -> ExtraCharge.builder()
            .type(Type.RECEIPT)
            .description(key.description)
            .amount(key.amount.doubleValue())
            .currency("VED")
            .active(true)
            .apartments(key.apartments.stream()
                .map(apt -> ExtraCharge.Apt.builder()
                    .number(apt)
                    .build())
                .toList())
            .build())
        .sorted(Comparator
            .comparing(ExtraCharge::description))
        .toList();
  }

  @Data
  @Builder
  private static class ExtraChargeKey {

    private String columnAddr;
    private String description;
    private BigDecimal amount;
    @Builder.Default
    private Set<String> apartments = new HashSet<>();
  }
}
