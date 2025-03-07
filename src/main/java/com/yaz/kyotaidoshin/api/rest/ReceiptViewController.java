package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.core.service.CalculateReceiptService;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.ReceiptFileService;
import com.yaz.kyotaidoshin.core.service.ReceiptService;
import com.yaz.kyotaidoshin.core.service.domain.CalculatedReceipt;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkiverse.renarde.pdf.Pdf;
import io.quarkiverse.renarde.router.Router;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestPath;

@Slf4j
@Path("receipts/view")
@PermissionsAllowed(PermissionUtil.Receipts.WRITE)
@RequiredArgsConstructor
public class ReceiptViewController extends HxControllerWithUser<RenardeUserImpl> {

  private final Vertx vertx;
  private final CalculateReceiptService calculateReceiptService;
  private final EncryptionService encryptionService;
  private final ReceiptService receiptService;
  private final ReceiptFileService receiptFileService;

  @GET
  @Path("{id}")
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> index(@RestPath String id) {
//    i18n.setForCurrentRequest("es");
    final var keys = encryptionService.decryptObj(id, Receipt.Keys.class);

    return receiptService.get(keys.id())
        .map(receipt -> {

          if (isHxRequest()) {

            return concatTemplates(
                Templates.index$headerContainer(receipt, id),
                Templates.index$container(receipt, id)
            );
          }

          return Templates.index(receipt, id);
        });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> views(@RestPath String id) {
//    i18n.setForCurrentRequest("es");
    final var keys = encryptionService.decryptObj(id, Receipt.Keys.class);

    return calculateReceiptService.calculate(keys.buildingId(), keys.id())
        .map(Templates::views);
  }

  @GET
  @Produces(Pdf.APPLICATION_PDF)
  public Uni<TemplateInstance> buildingPdf(@RestPath String buildingId, @RestPath long id) {
//    i18n.setForCurrentRequest("es");

    return calculateReceiptService.calculate(buildingId, id)
        .map(Templates::building);
  }

  @GET
  @Produces(Pdf.APPLICATION_PDF)
  public Uni<TemplateInstance> aptPdf(@RestPath String buildingId, @RestPath long id, @RestPath String aptNumber) {
//    i18n.setForCurrentRequest("es");

    return calculateReceiptService.calculate(buildingId, id)
        .map(receipt -> {

          final var apartmentRecord = receipt.apartmentRecords().stream()
              .filter(apartment -> apartment.apartment().number().equals(aptNumber))
              .findFirst()
              .orElseThrow();

          return Templates.apt(receipt, apartmentRecord);
        });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> html(@RestPath String key) {

    final var keys = encryptionService.decryptObj(key, Receipt.Keys.class);

    return calculateReceiptService.calculate(keys.buildingId(), keys.id())
        .map(Templates::building);
  }

  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Uni<Response> zip(@RestPath String key) {
//    i18n.setForCurrentRequest("es");

    final var keys = encryptionService.decryptObj(key, Receipt.Keys.class);

    return receiptFileService.zip(keys)
        .map(java.nio.file.Path::toString)
        .map(encryptionService::encrypt)
        .map(str -> Response.ok()
            .header("HX-Redirect", Router.getURI(FileController::download, str))
            .build());

  }

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance index(Receipt receipt, String id);

    public static native TemplateInstance index$headerContainer(Receipt receipt, String id);

    public static native TemplateInstance index$container(Receipt receipt, String id);

    public static native TemplateInstance views(CalculatedReceipt dto);

    public static native TemplateInstance building(CalculatedReceipt dto);

    public static native TemplateInstance apt(CalculatedReceipt dto, CalculatedReceipt.ApartmentRecord apartment);
  }


}
