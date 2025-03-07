package com.yaz.kyotaidoshin.api.domain.response;

import com.yaz.kyotaidoshin.persistence.model.OidcDbToken;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


public record SessionTableResponse(
    long totalCount,
    String lastKey,
    List<Item> results
) {

  @Data
  @Builder
  @RequiredArgsConstructor
  public static class Item {

    private final String key;
    private final OidcDbToken token;
    private final String cardId;

    @Getter(lazy = true)
    private final String tokenDuration = genTokenDuration();

//    @Getter(lazy = true)
//    private final String deleteUrl = genDeleteUrl();

    @Getter(lazy = true)
    private final String cardIdRef = genCardIdRef();

//    public String genDeleteUrl() {
//      return OidcDbTokenResource.DELETE_PATH + getToken().id();
//    }

    public String genCardIdRef() {
      return "#" + cardId;
    }

    public String genTokenDuration() {

      final var expiresIn = TimeUnit.SECONDS.toMillis(getToken().expiresIn());

      final var createdAt = getToken().createdAt().toInstant(ZoneOffset.UTC).toEpochMilli();

      if (true) {
        return Duration.ofMillis(expiresIn - createdAt).toString().replace("PT", "");
      }

      return ConvertUtil.formatDuration(expiresIn - createdAt);
    }
  }


}
