package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkiverse.renarde.security.ControllerWithUser;
import io.quarkiverse.renarde.security.LoginPage;
import io.quarkiverse.renarde.security.RenardeSecurity;
import io.quarkus.oidc.runtime.TenantConfigBean;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import java.util.Collection;


@Path("")
public class Login extends ControllerWithUser<RenardeUserImpl> {

  @Inject
  RenardeSecurity security;
  @Inject
  TenantConfigBean tenantConfigBean;

  /**
   * Login page
   */
  @LoginPage

  public Uni<TemplateInstance> login() {
    final var tenants = tenantConfigBean.getStaticTenantsConfig().keySet().stream().sorted().toList();
    return Uni.createFrom().item(Templates.login(tenants));
  }
//  private void checkLogoutFirst() {
//    if(getUser() != null) {
//      logoutFirst();
//    }
//  }

  @CheckedTemplate
  static class Templates {

    public static native TemplateInstance login(Collection<String> tenants);
//    public static native TemplateInstance logoutFirst();
//    public static native TemplateInstance welcome();
  }

//  /**
//   * Welcome page at the end of registration
//   */
//  @Authenticated
//  public TemplateInstance welcome() {
//    return Templates.welcome();
//  }
//
//  /**
//   * Link to logout page
//   */
//  public TemplateInstance logoutFirst() {
//    return Templates.logoutFirst();
//  }
}
