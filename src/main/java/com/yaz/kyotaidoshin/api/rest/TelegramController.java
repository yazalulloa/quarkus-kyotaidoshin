package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@Authenticated
@Path("telegram")
@RequiredArgsConstructor
public class TelegramController extends HxControllerWithUser<RenardeUserImpl> {

  @ConfigProperty(name = "app.telegram.start_url")
  String startUrl;

  @GET
  @Blocking
  public Response link() {
    final var user = getUser();
    if (user == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    return Response.temporaryRedirect(URI.create(startUrl + ConvertUtil.formatUserId(user.user().id()))).build();
  }
}
