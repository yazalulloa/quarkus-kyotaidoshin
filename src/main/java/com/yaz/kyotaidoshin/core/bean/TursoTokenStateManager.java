package com.yaz.kyotaidoshin.core.bean;

import com.yaz.kyotaidoshin.core.service.OidcDbTokenService;
import com.yaz.kyotaidoshin.util.DateUtil;
import io.quarkus.oidc.AuthorizationCodeTokens;
import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TokenStateManager;
import io.quarkus.oidc.runtime.CodeAuthenticationMechanism;
import io.quarkus.security.AuthenticationCompletionException;
import io.quarkus.security.AuthenticationFailedException;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@Alternative
@Priority(1)
public class TursoTokenStateManager implements TokenStateManager {

  private static final String TOKEN_STATE_INSERT_FAILED = "Failed to insert token state into database";
  private static final String FAILED_TO_ACQUIRE_TOKEN = "Failed to acquire authorization code tokens";

  private final OidcDbTokenService service;

  @Override
  public Uni<String> createTokenState(RoutingContext event, OidcTenantConfig oidcConfig,
      AuthorizationCodeTokens tokens, OidcRequestContext<String> requestContext) {

    final var now = DateUtil.epochSecond();
    final String id = now + UUID.randomUUID().toString();
    final var expiresIn = now + event.<Long>get(CodeAuthenticationMechanism.SESSION_MAX_AGE_PARAM);

    log.debug("AuthorizationCodeTokens: {} {} {} {}", id, tokens.getIdToken(), tokens.getAccessToken(),
        tokens.getAccessTokenExpiresIn());
    log.debug("Inserting token state into database: {}", id);

    return service.insert(tokens.getIdToken(), tokens.getAccessToken(),
            tokens.getRefreshToken(), expiresIn, id)
        .onFailure()
        .invoke(throwable -> log.error("Failed to insert token state into database: ", throwable))
        .onFailure()
        .transform(throwable -> new AuthenticationFailedException(TOKEN_STATE_INSERT_FAILED, throwable))
        .flatMap(affected -> {
          if (true) {
            log.debug("Token state inserted: {} {}", id, affected);
            return Uni.createFrom().item(id);
          }
          log.debug(TOKEN_STATE_INSERT_FAILED + " {}", id);
          return Uni.createFrom().failure(new AuthenticationFailedException(TOKEN_STATE_INSERT_FAILED));
        })
        .memoize().indefinitely();
  }

  @Override
  public Uni<AuthorizationCodeTokens> getTokens(RoutingContext routingContext, OidcTenantConfig oidcConfig,
      String tokenState,
      OidcRequestContext<AuthorizationCodeTokens> requestContext) {

    return service.read(tokenState)
        .onFailure()
        .transform(throwable -> new AuthenticationCompletionException(FAILED_TO_ACQUIRE_TOKEN, throwable))
        .flatMap(optional -> {
          if (optional.isPresent()) {
            final var tokens = optional.get();
            return Uni.createFrom().item(new AuthorizationCodeTokens(
                tokens.idToken(),
                tokens.accessToken(),
                tokens.refreshToken()));
          }
          log.debug("Token does not exist: {}", tokenState);
          return Uni.createFrom().failure(new AuthenticationCompletionException("Token does not exist in database " + tokenState));

        })
        .memoize().indefinitely()
        ;
  }

  @Override
  public Uni<Void> deleteTokens(RoutingContext routingContext, OidcTenantConfig oidcConfig, String tokenState,
      OidcRequestContext<Void> requestContext) {

    return service.delete(tokenState)
        .replaceWithVoid()
        .onFailure()
        .recoverWithItem(throwable -> {
          log.debug("Failed to delete tokens: {}", throwable.getMessage());
          return null;
        });
  }
}
