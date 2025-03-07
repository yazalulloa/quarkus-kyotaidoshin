package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeFormResponse;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeTableItem;
import com.yaz.kyotaidoshin.core.service.ApartmentService;
import com.yaz.kyotaidoshin.core.service.BuildingService;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.persistence.domain.ExtraChargeCreateRequest;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
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
import io.vertx.core.json.Json;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DELETE;
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
import org.jboss.resteasy.reactive.RestPath;

@Slf4j
@Path("extraCharges")
@PermissionsAllowed(value = {PermissionUtil.Receipts.WRITE, PermissionUtil.Buildings.WRITE})
@RequiredArgsConstructor
public class ExtraChargeController extends HxControllerWithUser<RenardeUserImpl> {

  private final BuildingService buildingService;
  private final ApartmentService apartmentService;
  private final EncryptionService encryptionService;
  private final ExtraChargeService service;

  @DELETE
  @Path("{key}")
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> delete(@NotBlank @RestPath String key) {

    final var json = encryptionService.decrypt(key);
    final var keys = Json.decodeValue(json, ExtraCharge.Keys.class);

    return Uni.combine().all()
        .unis(service.delete(keys), service.count(keys))
        .with((i, count) -> Templates.counters(i > 0 ? count - 1 : count));
  }

  @PUT
  @Path("/")
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> upsert(@BeanParam ExtraChargeUpsertRequest request) {
    final var keys = encryptionService.decryptObj(request.getKey(), ExtraCharge.Keys.class);

    return Uni.combine()
        .all()
        .unis(buildingService.exists(keys.buildingId()), apartmentService.aptByBuildings(keys.buildingId()),
            service.read(keys))
        .withUni((buildingExists, apartments, extraChargeOpt) -> {

          final var amount = Optional.ofNullable(request.getAmount())
              .map(StringUtil::trimFilter)
              .map(DecimalUtil::ofString)
              .orElse(BigDecimal.ZERO);

          final var aptsSelected = apartments.stream()
              .map(ExtraCharge.Apt::number)
              .filter(number -> request.getApts() != null && request.getApts().contains(number))
              .toList();
          final var currency = Optional.ofNullable(request.getCurrency()).orElse("VED");

          final var description = StringUtil.trimFilter(request.getDescription());
          final var apts = apartments.stream()
              .filter(apt -> aptsSelected.contains(apt.number()))
              .toList();

          final var built = ExtraCharge.builder()
              .parentReference(keys.parentReference())
              .buildingId(keys.buildingId())
              .id(keys.id())
              .type(keys.type())
              .description(description)
              .amount(amount.doubleValue())
              .currency(currency)
              .active(request.isActive())
              .apartments(apts)
              .build();

          final var newKeys = built.keys(keys.cardId(), keys.receiptId());

          String generalFieldError = null;
          if (!buildingExists) {
            generalFieldError = "Edificio no existe";
          } else if (aptsSelected.isEmpty()) {
            generalFieldError = "Debe seleccionar al menos un apartamento";
          } else if (extraChargeOpt.isPresent() && keys.hash() != 0
              && newKeys.hash() == keys.hash()) {
            generalFieldError = "No hay cambios";
          } else if (!TemplateGlobals.ALLOWED_CURRENCIES.contains(currency)) {
            generalFieldError = "Moneda invalida";
          }

          final var formResponse = ExtraChargeFormResponse.builder()
              .descriptionFieldError(
                  description == null ? "No puede estar vacio" : null)
              .amountFieldError(DecimalUtil.zeroOrLess(amount) ? "Debe ser mayor a 0" : null)
              .generalFieldError(generalFieldError)
              .aptsSelected(aptsSelected)
              .build();

          if (!formResponse.isSuccess()) {
            return TemplateUtil.templateUni(Templates.responseForm(formResponse));
          }

          if (extraChargeOpt.isEmpty()) {
            final var createRequest = ExtraChargeCreateRequest.builder()
                .parentReference(keys.parentReference())
                .buildingId(keys.buildingId())
                .type(keys.type())
                .description(description)
                .amount(amount.doubleValue())
                .currency(currency)
                .active(request.isActive())
                .apartments(aptsSelected)
                .build();

            return Uni.combine().all()
                .unis(service.create(createRequest), service.count(keys))
                .with((extraCharge, count) -> {

                  extraCharge = extraCharge.toBuilder()
                      .apartments(apts)
                      .build();

                  final var keys1 = extraCharge.keys(keys.receiptId());
                  final var tableItem = ExtraChargeTableItem.builder()
                      .key(encryptionService.encryptObj(keys1))
                      .item(extraCharge)
                      .cardId(keys1.cardId())
                      .outOfBoundsUpdate(false)
                      .addAfterEnd(true)
                      .build();

                  return ExtraChargeFormResponse.builder()
                      .tableItem(tableItem)
                      .count(count + 1)
                      .build();
                })
                .map(Templates::responseForm);
          } else {
            final var tableItem = ExtraChargeTableItem.builder()
                .key(encryptionService.encryptObj(newKeys))
                .cardId(newKeys.cardId())
                .item(built)
                .outOfBoundsUpdate(true)
                .addAfterEnd(false)
                .build();

            return service.update(built)
                .map(i -> ExtraChargeFormResponse.builder()
                    .tableItem(tableItem)
                    .build())
                .map(Templates::responseForm);
          }
        });
  }

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance counters(long count);

    public static native TemplateInstance item(ExtraChargeTableItem item);

    public static native TemplateInstance form(ExtraChargeInitFormDto dto);

    public static native TemplateInstance extraCharges(List<ExtraChargeTableItem> extraCharges);

    public static native TemplateInstance responseForm(ExtraChargeFormResponse dto);
  }

  @Data
  public static class ExtraChargeUpsertRequest {

    @NotBlank
    @RestForm
    private String key;
    @RestForm
    private String description;
    @RestForm
    private String amount;
    @RestForm
    private String currency;
    @RestForm
    private boolean active;
    @RestForm
    private Set<String> apts;
  }
}
