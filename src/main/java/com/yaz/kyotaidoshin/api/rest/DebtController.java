package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.debt.DebtCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.debt.DebtFormResponse;
import com.yaz.kyotaidoshin.api.domain.response.debt.DebtTableItem;
import com.yaz.kyotaidoshin.core.service.BuildingService;
import com.yaz.kyotaidoshin.core.service.DebtService;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.DecimalUtil;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.StringUtil;
import com.yaz.kyotaidoshin.util.TemplateGlobals;
import com.yaz.kyotaidoshin.util.TemplateUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;

@Slf4j
@Path("debts")
@PermissionsAllowed(PermissionUtil.Receipts.WRITE)
@RequiredArgsConstructor
public class DebtController extends HxControllerWithUser<RenardeUserImpl> {

  private final EncryptionService encryptionService;
  private final DebtService debtService;
  private final BuildingService buildingService;

  @PUT
  @Path("/")
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> update(@BeanParam DebtUpdateRequest request) {

    final var keys = encryptionService.decryptObj(request.getKey(), Debt.Keys.class);

    final var amount = Optional.ofNullable(request.getAmount())
        .map(StringUtil::trimFilter)
        .map(DecimalUtil::ofString)
        .orElse(BigDecimal.ZERO);

    final var previousPaymentAmount = Optional.ofNullable(request.getPreviousPaymentAmount())
        .map(StringUtil::trimFilter)
        .map(DecimalUtil::ofString)
        .orElse(BigDecimal.ZERO);

    final var update = Debt.builder()
        .buildingId(keys.buildingId())
        .receiptId(keys.receiptId())
        .aptNumber(keys.aptNumber())
        .receipts(request.getReceipts())
        .amount(amount)
        .months(request.getMonths())
        .previousPaymentAmount(previousPaymentAmount)
        .previousPaymentAmountCurrency(Optional.ofNullable(request.getPreviousPaymentAmountCurrency())
            .filter(
                TemplateGlobals.ALLOWED_CURRENCIES::contains)
            .orElse("VED"))
        .build();

    final var newKeys = update.keys(keys.cardId());

    if (newKeys.hash() == keys.hash()) {
      return TemplateUtil.templateUni(Templates.responseForm(DebtFormResponse.builder()
          .generalFieldError("No hay cambios para guardar")
          .build()));

    }
//    else if (true) {
//
//      return debtService.get(keys)
//          .invoke(debt -> {
//            log.info("DB debt {} new debt {}", debt, update);
//          })
//          .replaceWith(Templates.responseForm(DebtFormResponse.builder()
//              .generalFieldError("Si hay cambios para guardar")
//              .build()));
//    }

    return Uni.combine().all()
        .unis(debtService.update(update), buildingService.get(keys.buildingId()),
            debtService.readByReceipt(keys.buildingId(), keys.receiptId()))
        .with((i, building, debts) -> {

          final var debt = debts.stream()
              .filter(d -> d.aptNumber().equals(keys.aptNumber()))
              .findFirst()
              .map(Debt::aptName)
              .map(name -> update.toBuilder()
                  .aptName(name)
                  .build())
              .orElseThrow();
          debts.removeIf(d -> d.aptNumber().equals(keys.aptNumber()));
          debts.add(debt);
          final var debtTotal = debts.stream().map(Debt::amount).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);

          return DebtFormResponse.builder()
              .tableItem(DebtTableItem.builder()
                  .key(encryptionService.encryptObj(newKeys))
                  .item(debt)
                  .currency(building.debtCurrency())
                  .cardId(newKeys.cardId())
                  .outOfBoundsUpdate(true)
                  .build())
              .counters(DebtCountersDto.builder()
                  .count(debts.size())
                  .receipts(debts.stream().map(Debt::receipts).reduce(Integer::sum).orElse(0))
                  .total(ConvertUtil.numberFormat(building.debtCurrency()).format(debtTotal))
                  .build())
              .build();
        })
        .map(Templates::responseForm);
  }

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance counters(DebtCountersDto dto);

    public static native TemplateInstance grid(List<DebtTableItem> list);

    public static native TemplateInstance responseForm(DebtFormResponse dto);
  }

  @Data
  public static class DebtUpdateRequest {

    @RestForm
    String key;
    @RestForm
    int receipts;
    @RestForm
    String amount;
    @RestForm("month_input")
    Set<Integer> months;
    @RestForm
    String previousPaymentAmount;
    @RestForm
    String previousPaymentAmountCurrency;
  }


}
