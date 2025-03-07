package com.yaz.kyotaidoshin.core.bean;

import com.yaz.kyotaidoshin.util.MyOidcSetup;
import io.quarkiverse.renarde.security.RenardeSecurity;
import io.quarkiverse.renarde.security.RenardeTenantProvider;
import io.quarkus.oidc.UserInfo;
import io.quarkus.security.StringPermission;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class RolesAugmentor implements SecurityIdentityAugmentor {

  @Inject
  RenardeTenantProvider tenantProvider;
  @Inject
  MyOidcSetup oidcSetup;

  @Override
  public int priority() {
    return -1;
  }

  @Override
  public Uni<SecurityIdentity> augment(SecurityIdentity securityIdentity,
      AuthenticationRequestContext authenticationRequestContext) {

    if (securityIdentity.isAnonymous()) {
      return Uni.createFrom().item(securityIdentity);
    }

    final var attributes = securityIdentity.getAttributes();
    final var tenantId = Optional.ofNullable(tenantProvider.getTenantId())
        .or(() -> {

          log.error("Tenant provider is null");

          return Optional.ofNullable(attributes.get("tenant-id"))
              .map(String.class::cast);
        })
        .orElse(null);

    final var authId = Optional.ofNullable(RenardeSecurity.getUserId(securityIdentity.getPrincipal()))
        .or(() -> {

          log.error("RenardeSecurity.getUserId is null");
          if (securityIdentity.isAnonymous()) {
            return Optional.empty();
          }

          if (tenantId == null) {
            return Optional.empty();
          }

          return Optional.ofNullable(attributes.get("userinfo"))
              .filter(UserInfo.class::isInstance)
              .map(UserInfo.class::cast)
              .map(userInfo -> {
                return switch (tenantId) {
                  case "github" -> String.valueOf(userInfo.getLong("id"));
                  default -> userInfo.getSubject();
                };
              });
        })
        .orElse(null);

    if (securityIdentity.isAnonymous() || tenantId == null || authId == null) {
      log.info("Anonymous user");
      return Uni.createFrom().item(securityIdentity);
    }

    return oidcSetup.getUser(tenantId, authId)
        .map(optional -> {

          if (optional.isEmpty()) {
            return securityIdentity;
          }

          final var user = optional.get();
          QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(securityIdentity);
          builder.addAttribute("userId", user.userId());
          user.roles().forEach(builder::addRole);
          final var map = new HashMap<String, Set<String>>();
          user.roles().forEach(str -> {
            final var split = str.split(":");
            if (split.length == 2) {
              map.computeIfAbsent(split[0], k -> new HashSet<>()).add(split[1]);
            } else {
              map.computeIfAbsent(str, k -> new HashSet<>());
            }

          });

          map.forEach((k, v) -> {
            final var permission = new StringPermission(k, v.toArray(new String[0]));
            builder.addPermission(permission);
          });

//          final var permissionSet = user.roles().stream().map(StringPermission::new)
//              .collect(Collectors.<Permission>toSet());
//          builder.addPermissions(permissionSet);

          return builder.build();
        });
  }
}
