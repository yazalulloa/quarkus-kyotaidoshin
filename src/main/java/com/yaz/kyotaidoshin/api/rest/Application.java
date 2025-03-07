package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.InitResponse;
import com.yaz.kyotaidoshin.api.domain.response.InitResponse.Page;
import com.yaz.kyotaidoshin.persistence.model.Permission;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.ExecutorRecorder;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Authenticated
@RequiredArgsConstructor
public class Application extends HxControllerWithUser<RenardeUserImpl> {

  @Inject
  @Named(PermissionUtil.PAGE_PERMS_KEY)
  Map<String, Page> pagePerms;

  private List<Page> readPages(Set<String> perms) {

    final var pages = new ArrayList<Page>();
    pagePerms.forEach((key, page) -> {
      if (perms.contains(key)) {

        final var text = i18n.getMessage(page.text());
         pages.add(page.toBuilder()
             .text(text)
             .build());
      }
    });

    return pages;
  }

  @Path("/")
  public Uni<TemplateInstance> index() {
    return Uni.createFrom().item(Templates.index());
  }

  @Path("/init")
  public Uni<TemplateInstance> init() {

    return Uni.createFrom().item(this::getUser)
        .map(renardeUser -> {

          if (renardeUser.permissions().isEmpty()) {
            throw new RuntimeException("No permissions found for user " + renardeUser);
          }

          final var perms = renardeUser.permissions().stream().map(Permission::type).collect(Collectors.toSet());
          final var pages = readPages(perms);

          final var referer = httpHeaders.getHeaderString("referer");
          final var str = referer.substring(8);
          final var indexOf = str.indexOf("/");
          final var shouldRoute = indexOf == -1 || str.length() - 1 == indexOf;

          final var initResponse = InitResponse.builder()
              .picture(renardeUser.user().picture())
              .shouldRoute(shouldRoute)
              .pages(pages)
              .build();

          return Templates.init(initResponse);
        })
        .runSubscriptionOn(ExecutorRecorder.getCurrent());
  }

  @Path("/about")
  public TemplateInstance about() {
    return Templates.about();
  }

  public void french() {
    i18n.set("fr");
    index();
  }

  public void spanish() {
    i18n.set("es");
    index();
  }

  public void english() {
    i18n.set("en");
    index();
  }

  @Path("logout")
  public Uni<Response> logout() {
    return oidcSession.logout()
        .replaceWith(security::makeLogoutResponse);
  }

  @CheckedTemplate
  static class Templates {

    public static native TemplateInstance index();

    public static native TemplateInstance init(InitResponse res);

    public static native TemplateInstance about();
  }
}
