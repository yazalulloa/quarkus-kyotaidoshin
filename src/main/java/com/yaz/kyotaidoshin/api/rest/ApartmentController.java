package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.request.ApartmentRequest;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentFormDto;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentFormDto.EmailForm;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentTableResponse;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentUpsertFormDto;
import com.yaz.kyotaidoshin.core.service.ApartmentService;
import com.yaz.kyotaidoshin.core.service.BuildingService;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.persistence.domain.ApartmentQuery;
import com.yaz.kyotaidoshin.persistence.model.Apartment;
import com.yaz.kyotaidoshin.util.DecimalUtil;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingJsonFile;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.RxUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Slf4j
@Path("apartments")
@PermissionsAllowed(PermissionUtil.Apartments.READ)
@RequiredArgsConstructor
public class ApartmentController extends HxControllerWithUser<RenardeUserImpl> {

  private final ApartmentService apartmentService;
  private final BuildingService buildingService;
  private final EncryptionService encryptionService;

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance index(List<String> buildingIds);

    public static native TemplateInstance index$headerContainer(List<String> buildingIds);

    public static native TemplateInstance index$container();

    public static native TemplateInstance apartments(ApartmentTableResponse res);

    public static native TemplateInstance item(ApartmentTableResponse.Item item);

    public static native TemplateInstance counters(ApartmentCountersDto dto);

    public static native TemplateInstance buildingSelector(List<String> list);

    public static native TemplateInstance formDialog(List<String> buildings);

    public static native TemplateInstance upsert(ApartmentUpsertFormDto dto);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/")
  public Uni<TemplateInstance> index() {

    return buildingService.ids().map(buildingIds -> {

      if (isHxRequest()) {
        return concatTemplates(
            Templates.index$headerContainer(buildingIds),
            Templates.index$container()
        );
      }

      return Templates.index(buildingIds);
    });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> buildingSelector() {
    return buildingService.ids()
        .map(Templates::buildingSelector);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> search(
      @RestQuery String lastKey,
      @RestQuery("apt_search_input") String q,
      @RestQuery("building_input") Set<String> building) {

    final var keys = Optional.ofNullable(lastKey)
        .map(StringUtil::trimFilter)
        .map(str -> encryptionService.decryptObj(str, Apartment.Keys.class));

    final var apartmentQuery = ApartmentQuery.builder()
        .lastBuildingId(keys.map(Apartment.Keys::buildingId).orElse(null))
        .lastNumber(keys.map(Apartment.Keys::number).orElse(null))
        .q(StringUtil.trimFilter(q))
        .buildings(building)
        .build();

    return apartmentService.tableResponse(apartmentQuery)
        .map(Templates::apartments);
  }

  @DELETE
  @Path("{id}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Apartments.WRITE)
  public Uni<TemplateInstance> delete(
      @RestPath @NotBlank String id,
      @RestQuery("apt_search_input") String q,
      @RestQuery("building_input") Set<String> building) {

    final var keys = encryptionService.decryptObj(id, Apartment.Keys.class);

    final var apartmentQuery = ApartmentQuery.builder()
        .q(StringUtil.trimFilter(q))
        .buildings(building)
        .build();

    return Uni.combine().all()
        .unis(apartmentService.counters(apartmentQuery), apartmentService.delete(keys))
        .with((counters, i) -> {

          if (i > 0) {
            return counters.minusOne();
          }

          return counters;
        })
        .map(Templates::counters);
  }

  private Uni<ApartmentFormDto> fromRequest(ApartmentRequest request, boolean isUpdate) {
    final var buildingId = request.getBuildingId();
    final var number = request.getNumber();
    final var name = request.getName();

    final var numberFieldErrorUni = Uni.createFrom().deferred(() -> {
      if (isUpdate) {
        return Uni.createFrom().item((String) null);
      }

      if (buildingId != null && number != null) {
        return apartmentService.exists(buildingId, number)
            .map(bool -> bool ? "error_msg_apt_number_exists" : null);
      }

      return Uni.createFrom().item("error_msg_apt_number_invalid");
    });

    final var generalFieldErrorUni = Uni.createFrom()
        .deferred(() -> {
          if (isUpdate) {
            return apartmentService.get(buildingId, number)
                .map(optional -> {
                  if (optional.isEmpty()) {
                    return null;
                  }

                  final var apartment = optional.get();

                  final var noChange = Objects.equals(apartment.name(), request.getName())
                      && Objects.equals(apartment.number(), request.getNumber())
                      && Objects.equals(apartment.aliquot(), request.getAliquot())
                      && Objects.equals(apartment.emails(), request.getEmails());

                  return noChange ? "error_msg_apt_no_change" : null;
                });
          }

          return Uni.createFrom().item((String) null);
        });

    final var buildingFieldErrorUni = Optional.ofNullable(buildingId)
        .map(buildingService::exists)
        .map(uni -> uni.map(bool -> bool ? null : "error_msg_apt_building_does_not_exist"))
        .orElseGet(() -> Uni.createFrom().item("error_msg_apt_building_invalid"));

    return Uni.combine().all()
        .unis(buildingService.ids(), generalFieldErrorUni, numberFieldErrorUni, buildingFieldErrorUni)
        .with((buildings, generalFieldError, numberFieldError, buildingFieldError) -> {

          final var emailForms = request.getEmails().stream()
              .map(StringUtil::trimFilter)
              .map(str -> {

                final var error = str == null ? null : StringUtil.isValidEmail(str) ? null
                    : "error_msg_apt_email_invalid";

                return new EmailForm(str, error);
              })
              .toList();

          return ApartmentFormDto.builder()
              .key(request.getKey())
              .generalFieldError(generalFieldError)
              .buildings(buildings)
              .buildingId(buildingId)
              .buildingIdFieldError(buildingFieldError)
              .number(number)
              .numberFieldError(numberFieldError)
              .name(name)
              .nameFieldError(name == null ? "error_msg_apt_name_invalid" : null)
              .aliquot(request.getAliquot())
              .aliquotFieldError(
                  request.getAliquot() == null || DecimalUtil.zeroOrLess(request.getAliquot())
                      || DecimalUtil.greaterThan(request.getAliquot(), BigDecimal.valueOf(100))
                      ? "error_msg_apt_aliquot_invalid" : null)
              .emails(emailForms)
              .isEdit(isUpdate)
              .build();
        });
  }

  @PUT
  @Path("")
  @PermissionsAllowed(PermissionUtil.Apartments.WRITE)
  public Uni<TemplateInstance> upsert(@BeanParam ApartmentRequest request) {

    final var optKeys = Optional.ofNullable(StringUtil.trimFilter(request.getKey()))
        .map(str -> encryptionService.decryptObj(str, Apartment.Keys.class));

    optKeys.ifPresent(keys -> {
      request.setBuildingId(keys.buildingId());
      request.setNumber(keys.number());
    });

    final var isUpdate = optKeys.isPresent();
    return fromRequest(request, isUpdate)
        .map(dto -> {
          final var emailError = dto.emails().stream().map(EmailForm::error)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);

          final var generalError = Optional.ofNullable(dto.generalFieldError())
              .orElse(emailError);

          return ApartmentUpsertFormDto.builder()
              .buildingError(dto.buildingIdFieldError())
              .numberError(dto.numberFieldError())
              .nameError(dto.nameFieldError())
              .aliquotError(dto.aliquotFieldError())
              .generalError(generalError)
              .build();
        })
        .flatMap(dto -> {

          final var apartment = Apartment.builder()
              .buildingId(request.getBuildingId())
              .number(request.getNumber())
              .name(request.getName())
              .aliquot(request.getAliquot())
              .emails(request.getEmails())
              .build();

          if (optKeys.isPresent()) {
            if (apartment.keysWithHash(optKeys.get().cardId()).hash() == optKeys.get().hash()) {
              dto = dto.toBuilder()
                  .generalError("No hay cambios")
                  .build();
            }
          }

          if (dto.isSuccess()) {

            if (isUpdate) {
              dto = dto.toBuilder()
                  .item(ApartmentTableResponse.Item.builder()
                      .key(request.getKey())
                      .cardId(optKeys.get().cardId())
                      .item(apartment)
                      .outOfBoundUpdate(true)
                      .build())
                  .build();

              return apartmentService.update(apartment)
                  .replaceWith(dto);
            }

            return apartmentService.create(request)
                .replaceWith(dto);
          }

          return Uni.createFrom().item(dto);
        })
        .map(Templates::upsert);
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @PermissionsAllowed(PermissionUtil.Apartments.UPLOAD_BACKUP)
  public Uni<Response> upload(@RestForm FileUpload file) {

    final var pagingJsonFile = new PagingJsonFile();

    final var single = pagingJsonFile.pagingJsonFile(50, file.filePath().toString(), Apartment.class, list -> {
      return RxUtil.single(apartmentService.insert(list))
          .ignoreElement();
    }).toSingleDefault(Response.noContent().build());

    return MutinyUtil.toUni(single);
  }
}
