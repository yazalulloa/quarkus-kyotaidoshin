package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.UserTableResponse;
import com.yaz.kyotaidoshin.api.domain.response.UserTableResponse.NotificationKey;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.NotificationEventService;
import com.yaz.kyotaidoshin.core.service.UserService;
import com.yaz.kyotaidoshin.persistence.domain.UserQuery;
import com.yaz.kyotaidoshin.persistence.model.OidcDbToken.Keys;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.ExecutorRecorder;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Slf4j
@Path("users")
@PermissionsAllowed(PermissionUtil.Users.READ)
@RequiredArgsConstructor
public class UserController extends HxControllerWithUser<RenardeUserImpl> {

  private final UserService userService;
  private final EncryptionService encryptionService;
  private final NotificationEventService notificationEventService;

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

    final var query = UserQuery.builder()
        .lastId(lastId)
        .build();

    return userService.table(query)
        .map(Templates::users);
  }

  @DELETE
  @Path("{key}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Users.WRITE)
  public Uni<TemplateInstance> delete(@RestPath String key) {
    final var keys = encryptionService.decryptObj(key, Keys.class);
    return Uni.combine().all()
        .unis(userService.delete(keys.id()), userService.count())
        .with((i, count) -> Templates.counters(i > 0 ? count - i : count));
  }

  @POST
  @Path("/notification/{key}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Users.WRITE)
  public Uni<Response> notificationEvent(@NotBlank @RestPath String key, @RestForm boolean mode) {
    final var notificationKey = encryptionService.decryptObj(key, NotificationKey.class);

    Uni.createFrom().deferred(() -> {
          if (mode) {
            return notificationEventService.insert(notificationKey.key(), notificationKey.event());
          }
          return notificationEventService.delete(notificationKey.key(), notificationKey.event());

        })
//        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .runSubscriptionOn(ExecutorRecorder.getCurrent())
        .subscribe()
        .with(i -> {
          log.debug("Notification event {} {}", notificationKey, mode ? "inserted" : "deleted");
        }, throwable -> {
          log.debug("Error processing notification event {} ", notificationKey, throwable);
        });

    return Uni.createFrom().item(Response.noContent().build());
  }

  @CheckedTemplate
  static class Templates {

    public static native TemplateInstance index();

    public static native TemplateInstance index$headerContainer();

    public static native TemplateInstance index$container();

    public static native TemplateInstance users(UserTableResponse res);

    public static native TemplateInstance counters(long totalCount);

    public static native TemplateInstance dialogSelector(List<User> users);
  }
}
