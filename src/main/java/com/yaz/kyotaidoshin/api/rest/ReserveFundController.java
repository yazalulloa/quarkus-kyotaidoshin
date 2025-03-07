package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundFormResponse;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundTableItem;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.ExpenseService;
import com.yaz.kyotaidoshin.core.service.RateService;
import com.yaz.kyotaidoshin.core.service.ReceiptService;
import com.yaz.kyotaidoshin.core.service.ReserveFundService;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.model.ReserveFund;
import com.yaz.kyotaidoshin.persistence.model.domain.ExpenseType;
import com.yaz.kyotaidoshin.persistence.model.domain.ReserveFundType;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.DecimalUtil;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.StringUtil;
import com.yaz.kyotaidoshin.util.TemplateUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;

@Slf4j
@Path("reserveFunds")
@PermissionsAllowed(PermissionUtil.Buildings.WRITE)
@RequiredArgsConstructor
public class ReserveFundController extends HxControllerWithUser<RenardeUserImpl> {

  private final ReserveFundService service;
  private final EncryptionService encryptionService;
  private final ExpenseService expenseService;
  private final ReceiptService receiptService;
  private final RateService rateService;

  private Uni<Optional<Rate>> rateUni(long receiptId) {
    return receiptService.read(receiptId)
        .flatMap(opt -> {

          if (opt.isEmpty()) {
            return Uni.createFrom().item(Optional.empty());
          }

          final var receipt = opt.get();
          return rateService.get(receipt.rateId())
              .map(Optional::of);
        });
  }

  @DELETE
  @Path("{keysStr}")
  public Uni<Response> delete(@NotBlank @RestPath String keysStr) {
    final var keys = encryptionService.decryptObj(keysStr, ReserveFund.Keys.class);

    if (keys.buildingId() == null || keys.id() == 0) {
      return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
    }

    if (keys.receiptId() == 0) {
      return Uni.combine().all()
          .unis(service.count(keys.buildingId()), service.delete(keys.buildingId(), keys.id()))
          .with((count, i) -> ReserveFundCountersDto.count(count - i))
          .map(Templates::counters)
          .map(templateInstance -> Response.ok(templateInstance).build());
    }

    return Uni.combine().all()
        .unis(rateUni(keys.receiptId()),
            expenseService.readByReceipt(keys.receiptId()), service.listByBuilding(keys.buildingId()),
            service.count(keys.buildingId()), service.delete(keys.buildingId(), keys.id()))
        .with((optRate, expenses, reserveFunds, count, i) -> {

          final var expenseCountersDto = optRate.map(rate -> {
            reserveFunds.removeIf(r -> r.id() == keys.id());
            final var expensesCount = expenses.size();
            return expenseCountersDto(expensesCount, rate.rate(), expenses, reserveFunds);
          }).orElse(null);

          return ReserveFundCountersDto.builder()
              .count(count - i)
              .expenseCountersDto(expenseCountersDto)
              .build();
        })
        .map(Templates::counters)
        .map(templateInstance -> Response.ok(templateInstance).build());
  }

  @PUT
  @Path("/")
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> upsert(@BeanParam ReserveFundBeanRequest request) {

    final var keys = encryptionService.decryptObj(request.getKey(), ReserveFund.Keys.class);

    final var name = StringUtil.trimFilter(request.getName());

    final var fund = Optional.ofNullable(request.getFund())
        .map(StringUtil::trimFilter)
        .map(DecimalUtil::ofString)
        .orElse(BigDecimal.ZERO);

    final var expense = Optional.ofNullable(request.getExpense())
        .map(StringUtil::trimFilter)
        .map(DecimalUtil::ofString)
        .orElse(BigDecimal.ZERO);

    final var pay = Optional.ofNullable(request.getPay())
        .map(StringUtil::trimFilter)
        .map(DecimalUtil::ofString)
        .orElse(null);

    final var reserveFundType = Optional.ofNullable(request.getType())
        .orElse(ReserveFundType.PERCENTAGE);

    final var expenseType = Optional.ofNullable(request.getExpenseType())
        .orElse(ExpenseType.COMMON);

    AtomicReference<ReserveFundFormResponse> reserveFundFormResponse = new AtomicReference<>(
        ReserveFundFormResponse.builder()
            .nameFieldError(name == null || name.isEmpty() ? "No puede estar vacio" : null)
            .payFieldError(pay == null || DecimalUtil.zeroOrLess(pay) ? "Debe ser mayor a 0" : null)
            .build());

    if (!reserveFundFormResponse.get().isSuccess()) {
      return TemplateUtil.templateUni(Templates.responseForm(reserveFundFormResponse.get()));
    }

    final var reserveFund = ReserveFund.builder()
        .buildingId(keys.buildingId())
        .id(keys.id())
        .name(name)
        .fund(fund)
        .expense(expense)
        .pay(pay)
        .active(request.isActive())
        .type(reserveFundType)
        .expenseType(expenseType)
        .addToExpenses(request.isAddToExpenses())
        .build();

    return Uni.createFrom().deferred(() -> {

          if (keys.id() > 0) {
            final var newKeys = reserveFund.keys(keys.receiptId(), keys.cardId());
            if (newKeys.hash() == keys.hash()) {
              reserveFundFormResponse.set(reserveFundFormResponse.get().toBuilder()
                  .generalFieldError("No hay cambios para guardar")
                  .build());

              return Uni.createFrom().item(reserveFundFormResponse.get());
            }

            return service.update(reserveFund)
                .map(i -> {
                  final var newKey = encryptionService.encryptObj(newKeys);
                  return ReserveFundFormResponse.builder()
                      .tableItem(ReserveFundTableItem.builder()
                          .key(newKey)
                          .item(reserveFund)
                          .outOfBoundsUpdate(true)
                          .cardId(newKeys.cardId())
                          .build())
                      .build();
                });
          }

          return Uni.combine().all()
              .unis(service.count(keys.buildingId()), service.insert(reserveFund))
              .with((count, id) -> {

                final var built = reserveFund.toBuilder()
                    .id(id)
                    .build();
                final var newKeys = built.keys(keys.receiptId());
                final var newKey = encryptionService.encryptObj(newKeys);
                return ReserveFundFormResponse.builder()
                    .tableItem(ReserveFundTableItem.builder()
                        .key(newKey)
                        .item(built)
                        .addAfterEnd(true)
                        .cardId(newKeys.cardId())
                        .build())
                    .counters(ReserveFundCountersDto.count(count + 1))
                    .build();
              });
        })
        .flatMap(formResponse -> {

          if (keys.receiptId() == 0) {
            return Uni.createFrom().item(formResponse);
          }

          return Uni.combine().all()
              .unis(rateUni(keys.receiptId()),
                  expenseService.readByReceipt(keys.receiptId()), service.listByBuilding(keys.buildingId()))
              .with((optRate, expenses, reserveFunds) -> {

                final var expenseCountersDto = optRate.map(rate -> {
                  final var expensesCount = expenses.size();
                  return expenseCountersDto(expensesCount, rate.rate(), expenses, reserveFunds);
                }).orElse(null);

                return formResponse.toBuilder()
                    .expenseCountersDto(expenseCountersDto)
                    .build();
              });

        })
        .map(Templates::responseForm);
  }

  private ExpenseCountersDto expenseCountersDto(
      int expensesCount, BigDecimal rate, List<Expense> expenses, List<ReserveFund> reserveFunds) {
    final var expenseTotalsBeforeReserveFunds = ConvertUtil.expenseTotals(rate, expenses);

    final var reserveFundExpenses = ConvertUtil.reserveFundExpenses(expenseTotalsBeforeReserveFunds, reserveFunds,
        expenses);

    final var expenseTotals = ConvertUtil.expenseTotals(rate, expenses);

    return ExpenseCountersDto.builder()
        .count(expensesCount)
        .commonTotal(expenseTotalsBeforeReserveFunds.formatCommon())
        .unCommonTotal(expenseTotalsBeforeReserveFunds.formatUnCommon())
        .commonTotalPlusReserveFunds(expenseTotals.formatCommon())
        .unCommonTotalPlusReserveFunds(expenseTotals.formatUnCommon())
        .reserveFundExpenses(reserveFundExpenses)
        .build();
  }

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance counters(ReserveFundCountersDto dto);

    public static native TemplateInstance form(ReserveFundInitFormDto dto);

    public static native TemplateInstance item(ReserveFundTableItem item);

    public static native TemplateInstance responseForm(ReserveFundFormResponse dto);
  }

  @Data
  public static class ReserveFundBeanRequest {

    @RestForm
    String name;
    @RestForm
    String fund;
    @RestForm
    String expense;
    @RestForm
    String pay;
    @RestForm
    boolean active;
    @RestForm
    ReserveFundType type;
    @RestForm
    ExpenseType expenseType;
    @RestForm
    boolean addToExpenses;
    @RestForm
    @NotNull
    private String key;
  }
}
