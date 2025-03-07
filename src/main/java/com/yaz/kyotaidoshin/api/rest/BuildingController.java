package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.building.BuildingFormResponse;
import com.yaz.kyotaidoshin.api.domain.response.building.BuildingInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.building.BuildingInitFormDto.EmailConfig;
import com.yaz.kyotaidoshin.api.domain.response.building.BuildingTableResponse;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeTableItem;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundTableItem;
import com.yaz.kyotaidoshin.core.service.ApartmentService;
import com.yaz.kyotaidoshin.core.service.BuildingService;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.core.service.ReserveFundService;
import com.yaz.kyotaidoshin.core.service.domain.BuildingRecord;
import com.yaz.kyotaidoshin.core.service.download.BuildingDownloader;
import com.yaz.kyotaidoshin.core.service.mailer.MailerService;
import com.yaz.kyotaidoshin.persistence.domain.BuildingQuery;
import com.yaz.kyotaidoshin.persistence.model.Building;
import com.yaz.kyotaidoshin.persistence.model.Building.Keys;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.ReserveFund;
import com.yaz.kyotaidoshin.util.DecimalUtil;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingJsonFile;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.RxUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import com.yaz.kyotaidoshin.util.TemplateGlobals;
import com.yaz.kyotaidoshin.util.TemplateUtil;
import io.quarkiverse.renarde.router.Router;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Slf4j
@Path("buildings")
@PermissionsAllowed(PermissionUtil.Buildings.READ)
@RequiredArgsConstructor
public class BuildingController extends HxControllerWithUser<RenardeUserImpl> {

  private final BuildingService service;
  private final ApartmentService apartmentService;
  private final ExtraChargeService extraChargeService;
  private final EncryptionService encryptionService;
  private final ReserveFundService reserveFundService;
  private final BuildingDownloader downloader;
  private final MailerService mailerService;


  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance index();

    public static native TemplateInstance index$headerContainer();

    public static native TemplateInstance index$container();

    public static native TemplateInstance buildings(BuildingTableResponse res);

    public static native TemplateInstance counters(long totalCount);

    public static native TemplateInstance responseForm(BuildingFormResponse dto);

    public static native TemplateInstance form(BuildingInitFormDto dto);

    public static native TemplateInstance newIndex();

    public static native TemplateInstance editIndex(String id);

    public static native TemplateInstance editIndex$headerContainer(String id);

    public static native TemplateInstance editIndex$container(String id);

  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/")
  public Uni<TemplateInstance> index() {
    if (isHxRequest()) {
      return Uni.createFrom().item(concatTemplates(
          Templates.index$headerContainer(),
          Templates.index$container()
      ));
    }

    return Uni.createFrom().item(Templates::index);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> search(@RestQuery String lastKey) {

    final var keys = Optional.ofNullable(lastKey)
        .map(str -> encryptionService.decryptObj(str, Keys.class))
        .orElse(null);

    final var lastId = Optional.ofNullable(keys).map(Keys::id).orElse(null);

    return service.report(BuildingQuery.of(lastId))
        .map(Templates::buildings);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("new")
  @PermissionsAllowed(PermissionUtil.Buildings.WRITE)
  public Uni<TemplateInstance> newBuilding() {

    return Uni.createFrom().item(Templates::newIndex);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Buildings.WRITE)
  public Uni<TemplateInstance> edit(@RestPath String id) {
    if (isHxRequest()) {
      return Uni.createFrom().item(concatTemplates(
          Templates.editIndex$headerContainer(id),
          Templates.editIndex$container(id)
      ));
    }

    return Uni.createFrom().item(Templates.editIndex(id));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Buildings.WRITE)
  public Uni<TemplateInstance> form(@RestPath String id) {

    final var idStr = Optional.ofNullable(id)
        .map(String::trim)
        .filter(str -> !str.isEmpty())
        .filter(str -> !str.equalsIgnoreCase("new"))
        .orElse(null);

    if (idStr == null) {
      return Uni.createFrom().item(() -> {

            return BuildingInitFormDto.builder()
                .isEdit(false)
                .emailConfigs(mailerService.emailConfigs())
                .fixedPayAmount(BigDecimal.ZERO)
                .currenciesToShowAmountToPay(TemplateUtil.toStringArray(TemplateGlobals.ALLOWED_CURRENCIES))
                .build();
          })
          .map(Templates::form);
    }

    final var buildingUni = service.get(id);
    final var extraChargesUni = extraChargeService.by(id, id)
        .map(list -> list.stream()
            .map(extraCharge -> {
              final var keys = extraCharge.keys();
              return ExtraChargeTableItem.builder()
                  .item(extraCharge)
                  .key(encryptionService.encryptObj(keys))
                  .cardId(keys.cardId())
                  .build();
            })
            .toList());

    final var aptsUni = apartmentService.aptByBuildings(id);

    final var reserveFundsUni = reserveFundService.listByBuilding(id)
        .map(list -> {
          return list.stream()
              .map(reserveFund -> {
                final var keys = reserveFund.keys(0);
                return ReserveFundTableItem.builder()
                    .key(encryptionService.encryptObj(keys))
                    .item(reserveFund)
                    .cardId(keys.cardId())
                    .build();
              })
              .toList();
        });

    return Uni.combine().all()
        .unis(buildingUni, extraChargesUni, aptsUni, reserveFundsUni)
        .with((building, extraCharges, apts, reserveFunds) -> {

          final var key = encryptionService.encryptObj(building.keysWithHash());

          final var currenciesToShowAmountToPay = TemplateUtil.toStringArray(building.currenciesToShowAmountToPay());

          final var emailConfigs = mailerService.emailConfigs();
          String emailConfigId = null;
          if (building.emailConfigId() != null) {
            emailConfigId = emailConfigs.stream()
                .filter(emailConfig -> emailConfig.key().equals(building.emailConfigId()))
                .map(EmailConfig::id)
                .findFirst()
                .orElse(null);
          }

          return BuildingInitFormDto.builder()
              .isEdit(true)
              .key(key)
              .emailConfigs(emailConfigs)
              .id(building.id())
              .name(building.name())
              .rif(building.rif())
              .mainCurrency(building.mainCurrency())
              .debtCurrency(building.debtCurrency())
              .currenciesToShowAmountToPay(currenciesToShowAmountToPay)
              .fixedPay(building.fixedPay())
              .fixedPayAmount(Optional.ofNullable(building.fixedPayAmount()).orElse(BigDecimal.ZERO))
              .roundUpPayments(building.roundUpPayments())
              .emailConfigId(emailConfigId)
              .apts(apts)

              .extraChargeDto(ExtraChargeInitFormDto.builder()
                  .key(encryptionService.encryptObj(ExtraCharge.Keys.newBuilding(building.id())))
                  .extraCharges(extraCharges)
                  .build())

              .reserveFundDto(ReserveFundInitFormDto.builder()
                  .key(encryptionService.encryptObj(ReserveFund.Keys.ofBuilding(building.id())))
                  .reserveFunds(reserveFunds)
                  .build())

              .build();
        })
        .map(Templates::form);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> counters() {

    return service.count()
        .map(Templates::counters);
  }

  @DELETE
  @Path("{key}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Buildings.WRITE)
  public Uni<TemplateInstance> delete(@RestPath String key) {
    final var buildingId = encryptionService.decryptObj(key, Keys.class).id();
    return Uni.combine().all()
        .unis(service.delete(buildingId), service.count())
        .with((i, count) -> count - i)
        .map(Templates::counters);
  }

  private String fixedPayAmountFieldError(BuildingRequest request) {
    if (!request.isFixedPay()) {
      return null;
    }

    if (request.getFixedPayAmount() == null || request.getFixedPayAmount().isBlank()) {
      return "Monto fijo no puede estar vacio";
    }

    final var requestFixedPayAmount = DecimalUtil.ofString(request.getFixedPayAmount());

    if (requestFixedPayAmount == null || !DecimalUtil.greaterThanZero(requestFixedPayAmount)) {
      return "Monto fijo invalido";
    }

    return null;
  }

  @PUT
  @Path("/")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Buildings.WRITE)
  public Uni<Response> upsert(@BeanParam BuildingRequest request) {

    final var keysOpt = Optional.ofNullable(StringUtil.trimFilter(request.getKey()))
        .map(str -> encryptionService.decryptObj(str, Keys.class));

    keysOpt.ifPresent(keys -> request.setId(keys.id()));

    final var currenciesToShowAmountToPay = Optional.ofNullable(request.getCurrenciesToShowAmountToPay())
        .filter(set -> !set.isEmpty())
        .orElseGet(() -> Set.of(request.getMainCurrency()));

    final var requestFixedPayAmount = DecimalUtil.ofString(request.getFixedPayAmount());

    final var id = StringUtil.trimFilter(request.getId());
    final var name = StringUtil.trimFilter(request.getName());
    final var currenciesToShowAmountToPayStringArray = TemplateUtil.toStringArray(currenciesToShowAmountToPay);
    var formResponse = BuildingFormResponse.builder()
        .key(request.getKey())
        .idFieldError(id == null ? "ID no puede estar vacio" : null)
        .nameFieldError(name == null ? "Nombre no puede estar vacio" : null)
        .fixedPayAmountFieldError(fixedPayAmountFieldError(request))
        .currenciesToShowAmountToPay(currenciesToShowAmountToPayStringArray)
        .build();

    if (!formResponse.isSuccess()) {
      return TemplateUtil.responseUni(Templates.responseForm(formResponse));
    }

    final var building = Building.builder()
        .id(id)
        .name(name)
        .rif(request.getRif())
        .mainCurrency(request.getMainCurrency())
        .debtCurrency(request.getDebtCurrency())
        .currenciesToShowAmountToPay(currenciesToShowAmountToPay)
        .fixedPay(request.isFixedPay())
        .fixedPayAmount(requestFixedPayAmount)
        .roundUpPayments(request.isRoundUpPayments())
        .emailConfigId(encryptionService.decrypt(request.getEmailConfig()))
        .build();

    if (keysOpt.map(Keys::hash).map(hash -> hash == building.keysWithHash().hash()).orElse(false)) {
      formResponse = formResponse.toBuilder()
          .generalFieldError("No se ha modificado nada")
          .build();
    }

    if (!formResponse.isSuccess()) {
      return TemplateUtil.responseUni(Templates.responseForm(formResponse));
    }

    if (keysOpt.isPresent()) {
      return service.update(building)
          .map(b -> BuildingFormResponse.builder()
              .key(encryptionService.encryptObj(b.keysWithHash()))
              .generalFieldError("Actualizado")
              .currenciesToShowAmountToPay(currenciesToShowAmountToPayStringArray)
              .build())
          .map(Templates::responseForm)
          .map(t -> Response.ok(t).build());
    }

    return service.exists(building.id())
        .flatMap(bool -> {
          if (bool) {
            final var response = BuildingFormResponse.builder()
                .idFieldError("ID ya existe")
                .currenciesToShowAmountToPay(currenciesToShowAmountToPayStringArray)
                .build();
            return TemplateUtil.responseUni(Templates.responseForm(response));
          }

          return service.create(building)
              .map(Building::id)
              .map(buildingId -> Response.noContent()
                  .header("HX-Redirect", Router.getURI(BuildingController::edit, buildingId))
//                  .header("HX-Redirect", "/buildings/edit/" + buildingId)
                  .build());
        });
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Buildings.UPLOAD_BACKUP)
  public Uni<Response> upload(@RestForm FileUpload file) {

    final var pagingJsonFile = new PagingJsonFile();

    final var single = pagingJsonFile.pagingJsonFile(50, file.filePath().toString(), BuildingRecord.class, list -> {
      final var buildings = list.stream().map(BuildingRecord::building).toList();
      final var extraCharges = list.stream().map(BuildingRecord::extraCharges).flatMap(Collection::stream).toList();
      final var reserveFunds = list.stream().map(BuildingRecord::reserveFunds).flatMap(Collection::stream).toList();

      final var buildingUni = service.insertIgnore(buildings);
      final var extraChargeUni = extraChargeService.insertBulk(extraCharges);
      final var reserveFundUni = reserveFundService.insertBulk(reserveFunds);

      final var voidUni = Uni.combine().all()
          .unis(buildingUni, extraChargeUni, reserveFundUni)
          .discardItems();

      return RxUtil.completable(voidUni);

    }).toSingleDefault(Response.noContent().build());

    return MutinyUtil.toUni(single);
  }

  @Data
  public static class BuildingRequest {

    @RestForm
    private String key;
    @RestForm
    private String id;
    @RestForm
    private String name;
    @RestForm
    private String rif;
    @NotBlank
    @RestForm
    private String mainCurrency;
    @NotBlank
    @RestForm
    private String debtCurrency;
    @RestForm
    private Set<String> currenciesToShowAmountToPay;
    @RestForm
    private boolean fixedPay;
    @RestForm
    private String fixedPayAmount;
    @RestForm
    private boolean roundUpPayments;
    @RestForm
    private String emailConfig;

  }
}
