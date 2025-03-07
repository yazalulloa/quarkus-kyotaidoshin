package com.yaz.kyotaidoshin.api.domain.response;

import java.net.URI;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record InitResponse(
    String picture,
    boolean shouldRoute,
    List<Page> pages

) {

  @Builder(toBuilder = true)
  public record Page(
      String id,
      URI url,
      String text
  ) {

  }

}
