package com.yaz.kyotaidoshin.api;

import com.yaz.kyotaidoshin.api.rest.Login;
import io.quarkiverse.renarde.router.Router;
import io.quarkus.security.AuthenticationCompletionException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Slf4j
public class ExceptionMappers {

  private final URI loginUri = Router.getURI(Login::login);

  //  @ServerExceptionMapper
//  public RestResponse<String> forbiddenException(UriInfo uriInfo, ForbiddenException e) {
//    log.error("ForbiddenException {}", uriInfo.getAbsolutePath(), e);
//    return RestResponse.status(Status.FORBIDDEN);
//  }
//

//  @ServerExceptionMapper
//  public RestResponse<String> mapException(UriInfo uriInfo, UnauthorizedException x) {
//    final var path = uriInfo.getPath();
//
//    if (path.startsWith("/api")) {
//      log.info("UnauthorizedException {}", uriInfo.getAbsolutePath(), x);
//      final var response = RestResponse.ok("");
//      response.getHeaders().add("HX-Redirect", loginUri.toString());
//      return response;
//    }
//    log.info("{}", uriInfo.getAbsolutePath());
//    //return RestResponse.status(Response.Status.NOT_FOUND, "Unknown cheese: " + x.name);
//    log.info("UnauthorizedException: " + x.getMessage());
//
//    return RestResponse.temporaryRedirect(loginUri);
//  }

  @ServerExceptionMapper
  public RestResponse<String> mapException(UriInfo uriInfo, ContainerRequestContext requestContext,
      AuthenticationCompletionException x) {
    log.error("AuthenticationCompletionException {} to {}", uriInfo.getAbsolutePath(), loginUri, x);

    final var hxCurrent = requestContext.getHeaders().getFirst("Hx-Current-Url");
    if (hxCurrent != null) {

      log.info("AuthenticationCompletionException {}", uriInfo.getAbsolutePath(), x);
      final var response = RestResponse.ok("");
      response.getHeaders().add("HX-Redirect", loginUri.toString());
      return response;
    }

    return RestResponse.temporaryRedirect(loginUri);
  }
//
//  @ServerExceptionMapper
//  public RestResponse<String> mapException(UriInfo uriInfo, ContainerRequestContext requestContext, NotFoundException x)
//      throws URISyntaxException {
//
//    if (uriInfo.getPath().equals("/favicon.ico")
//        || uriInfo.getPath().equals("/robots.txt")
//        || uriInfo.getPath().equals("/login.html")
//        || uriInfo.getPath().contains("/examples/")) {
//      return RestResponse.status(404, "Not Found");
//    }
//
//    final var principal = requestContext.getSecurityContext().getUserPrincipal();
//
//    if (principal != null) {
//      log.info("Redirect to / User principal: {}", principal);
//      return RestResponse.temporaryRedirect(new URI("/"));
//    }
//
//    log.info("uriInfo.getPath(): " + uriInfo.getPath());
//
//    log.error("NotFoundException: {}", x.getMessage());
//
//    return RestResponse.temporaryRedirect(new URI("/login.html"));
//  }
}
