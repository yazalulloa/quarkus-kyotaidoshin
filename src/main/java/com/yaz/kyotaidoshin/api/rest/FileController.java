package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;

@Slf4j
@Path("files")
@RequiredArgsConstructor
public class FileController extends HxControllerWithUser<RenardeUserImpl> {


  private final EncryptionService encryptionService;

  @GET
  @Path("{key}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Uni<RestResponse<File>> download(@RestPath String key) {

    return Uni.createFrom().item(() -> {
      final var path = encryptionService.decrypt(key);
      final var file = new File(path);
      return ResponseBuilder.ok(file)
          .header("Content-Disposition", "attachment; filename=" + file.getName())
          .build();
    });
  }

}
