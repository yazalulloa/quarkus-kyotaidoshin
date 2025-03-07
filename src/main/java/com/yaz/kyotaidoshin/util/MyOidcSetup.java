package com.yaz.kyotaidoshin.util;


import com.yaz.kyotaidoshin.api.rest.Application;
import com.yaz.kyotaidoshin.core.service.OidcDbTokenService;
import com.yaz.kyotaidoshin.core.service.PermissionService;
import com.yaz.kyotaidoshin.core.service.UserService;
import com.yaz.kyotaidoshin.persistence.model.Permission;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import io.quarkiverse.renarde.oidc.RenardeOidcHandler;
import io.quarkiverse.renarde.oidc.RenardeOidcSecurity;
import io.quarkiverse.renarde.router.Router;
import io.quarkiverse.renarde.security.RenardeUser;
import io.quarkiverse.renarde.security.RenardeUserProvider;
import io.quarkiverse.renarde.util.Flash;
import io.quarkiverse.renarde.util.RedirectException;
import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class MyOidcSetup implements RenardeUserProvider, RenardeOidcHandler {

  @Inject
  RenardeOidcSecurity oidcSecurity;

//  @Inject
//  RenardeSecurity security;

  @Inject
  Flash flash;

  @Inject
  UserService userService;
  @Inject
  PermissionService permissionService;
  @Inject
  UserInfo userInfo;

  @Inject
  SecurityIdentity securityIdentity;

  @Inject
  OidcDbTokenService tokenService;

  @Inject
  Event<UpdateSessionEvent> eventSender;

  @Override
  public RenardeUser findUser(String tenantId, String authId) {

//    log.info("findUser tenantID {} authId {} ", tenantId, authId);

    if (tenantId == null || tenantId.equals("manual")) {
      throw new RuntimeException("Invalid tenantId");
    } else {

      final var renardeUser = getUser(tenantId, authId).await().atMost(Duration.ofSeconds(5));
      return renardeUser.orElse(null);
    }
  }

  public Uni<Optional<RenardeUserImpl>> getUser(String tenantId, String authId) {
    final var identityProvider = IdentityProvider.valueOf(tenantId.toUpperCase());

    return userService.getFromProvider(identityProvider, authId)
        .flatMap(optional -> {
          if (optional.isEmpty()) {
            return Uni.createFrom().item(Optional.empty());
          }

          final var user = optional.get();

          return permissionService.selectByUser(user.id())
              .map(permissions -> {

                final var roles = permissions.stream().map(Permission::type)
                    .collect(Collectors.toSet());

                return RenardeUserImpl.builder()
                    .user(user)
                    .permissions(permissions)
                    .roles(roles)
                    .build();
              })
              .map(Optional::of);

        });
  }

  @Transactional
  @Override
  public void oidcSuccess(String tenantId, String authId) {
    log.info("OIDC SUCCESS tenantID {} authId {} ", tenantId, authId);
    final var identityProvider = IdentityProvider.valueOf(tenantId.toUpperCase());

    final var userId = saveUser(identityProvider, authId);
//    log.info("User saveIfExists {}", userId);
    var uri = Router.getURI(Application::index);
    final var redirectLocation = updateSession(userId);
    if (redirectLocation != null) {
      log.info("Redirecting to {}", redirectLocation);
      uri = URI.create(redirectLocation);
    }

//    User user = User.findByAuthId(tenantId, authId);
//    URI uri;
//    if(user == null) {
//      // registration
//      user = new User();
//      user.tenantId = tenantId;
//      user.authId = authId;
//
//      user.email = oidcSecurity.getOidcEmail();
//      // workaround for Twitter
//      if(user.email == null)
//        user.email = "twitter@example.com";
//      user.firstName = oidcSecurity.getOidcFirstName();
//      user.lastName = oidcSecurity.getOidcLastName();
//      user.userName = oidcSecurity.getOidcUserName();
//
//      user.status = UserStatus.CONFIRMATION_REQUIRED;
//      user.confirmationCode = UUID.randomUUID().toString();
//      user.persist();
//
//      // go to registration
//      uri = Router.getURI(Login::confirm, user.confirmationCode);
//    } else if(!user.registered()) {
//      // user exists, but not fully registered yet
//      // go to registration
//      uri = Router.getURI(Login::confirm, user.confirmationCode);
//    } else {
//      // regular login
//      uri = Router.getURI(Application::index);
//    }

    throw new RedirectException(Response.seeOther(uri).build());
  }

  private String updateSession(String userId) {
    final RoutingContext routingContext = securityIdentity.getAttribute(RoutingContext.class.getName());
    if (routingContext != null) {
      routingContext.request().cookies().stream()
          .filter(cookie -> cookie.getName().startsWith("q_session"))
          .map(Cookie::getValue)
          .forEach(sessionId -> {
            tokenService.updateUserId(sessionId, userId).await().atMost(Duration.ofSeconds(3));
//            final var event = new UpdateSessionEvent(sessionId, userId);
//            eventSender.fireAsync(event);
          });

      for (var cookie : routingContext.request().cookies()) {
        if (cookie.getName().equals("quarkus-redirect-location")) {
          return Optional.ofNullable(cookie.getValue())
              .map(String::trim)
              .filter(s -> !s.isEmpty())
//              .filter(str -> str.lastIndexOf("/") == str.length() - 1)
              .orElse(null);
        }
      }


    }

    return null;
  }

  public void updateSessionEvent(@ObservesAsync UpdateSessionEvent event) {
    tokenService.updateUserId(event.sessionId(), event.userId())
        .subscribe()
        .with(i -> log.info("session updated {} {}", event, i),
            t -> log.info("Error update oidc token {}", event, t));
  }

  private String saveUser(IdentityProvider identityProvider, String authId) {

//    final var principal = (OidcJwtCallerPrincipal) securityIdentity.getPrincipal();
    final var picture = Optional.ofNullable(userInfo.getString("picture"))
        .orElseGet(() -> userInfo.getString("avatar_url"));
    final var userData = userInfo.getJsonObject().toString();

    final var username = Optional.ofNullable(oidcSecurity.getOidcUserName())
        .orElseGet(() -> userInfo.getString("name"));

    final var user = User.builder()
        .provider(identityProvider)
        .providerId(authId)
        .email(oidcSecurity.getOidcEmail())
        .username(username)
        .firstName(oidcSecurity.getOidcFirstName())
        .lastName(oidcSecurity.getOidcLastName())
        .picture(picture)
        .data(new JsonObject(userData))
        .build();

//    log.info("User {}", user);

    return userService.saveIfExists(user).await().atMost(Duration.ofSeconds(3));
  }

  @Override
  public void loginWithOidcSession(String tenantId, String authId) {
    log.info("loginWithOidcSession tenantID {} authId {} ", tenantId, authId);
    final var identityProvider = IdentityProvider.valueOf(tenantId.toUpperCase());

    var optional = userService.getIdFromProvider(identityProvider, authId).await().atMost(Duration.ofSeconds(10));

    if (optional.isEmpty()) {
      log.info("Invalid user: {}", authId);
      flash.flash("message", "Invalid user: " + authId);
      optional = Optional.of(saveUser(identityProvider, authId));
    }

    flash.flash("message", "Already logged in");
    var uri = Router.getURI(Application::index);

    final var redirectLocation = updateSession(optional.get());
    if (redirectLocation != null) {
      uri = URI.create(redirectLocation);
    }
    throw new RedirectException(Response.seeOther(uri).build());

//    RenardeUser user = findUser(tenantId, authId);
//    // old cookie, no such user
//    if(user == null) {
//      flash.flash("message", "Invalid user: "+authId);
//      throw new RedirectException(security.makeLogoutResponse());
//    }
//    // redirect to registration
//    URI uri;
//    if(!user.registered()) {
//      uri = Router.getURI(Login::confirm, ((User)user).confirmationCode);
//    } else {
//      flash.flash("message", "Already logged in");
//      uri = Router.getURI(Application::index);
//    }
//    throw new RedirectException(Response.seeOther(uri).build());
  }

  public record UpdateSessionEvent(String sessionId, String userId) {

  }
}
