package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.permissions.PermissionCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.permissions.PermissionTableResponse;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.PermissionService;
import com.yaz.kyotaidoshin.core.service.UserService;
import com.yaz.kyotaidoshin.persistence.domain.PermissionQuery;
import com.yaz.kyotaidoshin.persistence.model.Permission;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Slf4j
@Path("permissions")
@PermissionsAllowed(PermissionUtil.Permissions.READ)
@RequiredArgsConstructor
public class PermissionController extends HxControllerWithUser<RenardeUserImpl> {

  private final PermissionService permissionService;
  private final EncryptionService encryptionService;
  private final UserService userService;

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance index(List<User> users);

    public static native TemplateInstance index$headerContainer(List<User> users);

    public static native TemplateInstance index$container();

    public static native TemplateInstance search(PermissionTableResponse res);

    public static native TemplateInstance counters(PermissionCountersDto dto);

    public static native TemplateInstance item(PermissionTableResponse.Item item);

    public static native TemplateInstance form(List<User> users);

    public static native TemplateInstance userPerms(Set<String> permissions);

    public static native TemplateInstance responseForm();
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/")
  public Uni<TemplateInstance> index() {
    return userService.all().map(users -> {

      if (isHxRequest()) {
        return concatTemplates(
            Templates.index$headerContainer(users),
            Templates.index$container()
        );
      }

      return Templates.index(users);
    });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> search(
      @RestQuery String lastKey,
      @RestQuery("permission_type_input") Set<String> types,
      @RestQuery("user_input") Set<String> users) {

    final var keys = Optional.ofNullable(lastKey)
        .map(StringUtil::trimFilter)
        .map(str -> encryptionService.decryptObj(str, Permission.Keys.class));

    final var apartmentQuery = PermissionQuery.builder()
        .lastKeys(keys.orElse(null))
        .userIds(users)
        .types(types)
        .build();

    return permissionService.search(apartmentQuery)
        .map(Templates::search);
  }

  @DELETE
  @Path("{key}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Permissions.WRITE)
  public Uni<TemplateInstance> delete(
      @RestPath String key,
      @RestQuery("permission_type_input") Set<String> types,
      @RestQuery("user_input") Set<String> users
  ) {
    final var keys = encryptionService.decryptObj(key, Permission.Keys.class);

    final var permissionQuery = PermissionQuery.builder()
        .types(types)
        .userIds(users)
        .build();

    return permissionService.delete(keys.userId(), keys.type())
        .replaceWith(permissionService.counters(permissionQuery))
        .map(Templates::counters);
  }

  @PUT
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Permissions.WRITE)
  public Uni<TemplateInstance> upsert(
      @NotBlank @RestForm("user_perm_select") String userId,
      @RestForm("perms") Set<String> perms) {

    return permissionService.selectByUser(userId)
        .map(permissions -> permissions.stream().map(Permission::type).collect(Collectors.toSet()))
        .flatMap(set -> {
          if (set.equals(perms)) {
            return Uni.createFrom().item(() -> null);
          }

          return permissionService.update(userId, perms)
              .replaceWith(Templates::responseForm);
        });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> userPerms(@NotBlank @RestQuery("user_perm_select") String userId) {
    return permissionService.selectByUser(userId)
        .map(permissions -> permissions.stream().map(Permission::type).collect(Collectors.toSet()))
//        .invoke(set -> log.info("User {} has permissions {}", userId, set))
        .map(Templates::userPerms);
  }

}
