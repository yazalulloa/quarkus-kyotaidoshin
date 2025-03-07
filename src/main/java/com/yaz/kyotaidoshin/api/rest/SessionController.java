package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.SessionTableResponse;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.OidcDbTokenService;
import com.yaz.kyotaidoshin.persistence.domain.OidcDbTokenQueryRequest;
import com.yaz.kyotaidoshin.persistence.model.OidcDbToken.Keys;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Slf4j
@Path("sessions")
@PermissionsAllowed(PermissionUtil.Sessions.READ)
@RequiredArgsConstructor
public class SessionController extends HxControllerWithUser<RenardeUserImpl> {

  private final OidcDbTokenService tokenService;
  private final EncryptionService encryptionService;

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
    final var lastId = Optional.ofNullable(lastKey)
        .map(key -> encryptionService.decryptObj(key, Keys.class))
        .map(Keys::id)
        .orElse(null);

    final var query = OidcDbTokenQueryRequest.builder()
        .lastId(lastId)
        .build();

    return tokenService.tableResponse(query)
        .map(Templates::sessions);
  }

  @DELETE
  @Path("{key}")
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> delete(@RestPath String key) {
    final var keys = encryptionService.decryptObj(key, Keys.class);
    return Uni.combine().all()
        .unis(tokenService.delete(keys.id()), tokenService.count())
        .with((i, count) -> Templates.counters(i > 0 ? count - i : count));
  }

  @PUT
  public Uni<Void> expires(@RestPath String key) {
    final var keys = encryptionService.decryptObj(key, Keys.class);
    return tokenService.expires(keys.id())
        .replaceWithVoid();
  }

  @CheckedTemplate
  static class Templates {

    public static native TemplateInstance index();

    public static native TemplateInstance index$headerContainer();

    public static native TemplateInstance index$container();

    public static native TemplateInstance sessions(SessionTableResponse res);

    public static native TemplateInstance counters(long totalCount);
  }

}
