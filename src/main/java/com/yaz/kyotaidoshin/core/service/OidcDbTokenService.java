package com.yaz.kyotaidoshin.core.service;


import com.yaz.kyotaidoshin.api.domain.response.SessionTableResponse;
import com.yaz.kyotaidoshin.core.service.cache.OidcDbTokenCache;
import com.yaz.kyotaidoshin.persistence.domain.OidcDbTokenQueryRequest;
import com.yaz.kyotaidoshin.persistence.model.OidcDbToken;
import com.yaz.kyotaidoshin.persistence.repository.OidcDbTokenRepository;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class OidcDbTokenService {

  //private final Instance<OidcDbTokenRepository> repository;
  private final EncryptionService encryptionService;
  private final OidcDbTokenRepository repository;

  private OidcDbTokenRepository repository() {
    //return repository.get();
    return repository;
  }

  @CacheInvalidateAll(cacheName = OidcDbTokenCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = OidcDbTokenCache.QUERY_COUNT)
  @CacheInvalidate(cacheName = OidcDbTokenCache.EXISTS)
  public Uni<Void> invalidateOne(String id) {
    return invalidateGet(id);
  }

  @CacheInvalidateAll(cacheName = OidcDbTokenCache.SELECT)
  @CacheInvalidate(cacheName = OidcDbTokenCache.READ)
  public Uni<Void> invalidateGet(String id) {
    return Uni.createFrom().voidItem();
  }

  @CacheInvalidateAll(cacheName = OidcDbTokenCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = OidcDbTokenCache.QUERY_COUNT)
  @CacheInvalidateAll(cacheName = OidcDbTokenCache.EXISTS)
  @CacheInvalidateAll(cacheName = OidcDbTokenCache.SELECT)
  @CacheInvalidateAll(cacheName = OidcDbTokenCache.READ)
  public Uni<Void> invalidateAll() {
    return Uni.createFrom().voidItem();
  }


  @CacheResult(cacheName = OidcDbTokenCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count() {
    return repository().count();
  }

  @CacheResult(cacheName = OidcDbTokenCache.READ, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<OidcDbToken>> read(String id) {
    return repository().read(id);
  }

  public Uni<Integer> delete(String id) {

    return repository().delete(id)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(id)));
  }


  public Uni<Integer> deleteByUser(String id) {
    return repository().deleteByUser(id)
        .flatMap(MutinyUtil.cacheCall(invalidateAll()));
  }

  public Uni<Integer> deleteIfExpired(long expiresIn) {
    return repository().deleteIfExpired(expiresIn)
        .flatMap(MutinyUtil.cacheCall(invalidateAll()));
  }

  public Uni<Integer> insert(String idToken, String accessToken, String refreshToken, long expiresIn, String id) {
    return repository().insert(idToken, accessToken, refreshToken, expiresIn, id)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(id)));
  }

  public Uni<Integer> updateUserId(String id, String userId) {
    return repository().updateUserId(id, userId)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(id)));
  }

  @CacheResult(cacheName = OidcDbTokenCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<OidcDbToken>> list(OidcDbTokenQueryRequest queryRequest) {
    return repository().select(queryRequest);
  }

  public Uni<SessionTableResponse> tableResponse(OidcDbTokenQueryRequest request) {

    final var actualLimit = request.limit() + 1;

    final var queryRequest = request.toBuilder()
        .limit(actualLimit)
        .build();
    return Uni.combine().all().unis(
        count(),
        list(queryRequest)
    ).with((totalCount, tokens) -> {

      final var results = tokens.stream()
          .map(token -> {
            final var keys = token.keys();
            return SessionTableResponse.Item.builder()
                .key(encryptionService.encryptObj(keys))
                .token(token)
                .cardId(keys.cardId())
                .build();
          })
          .collect(Collectors.toCollection(() -> new ArrayList<>(tokens.size())));

      String lastKey = null;
      if (results.size() == actualLimit) {
        results.removeLast();
        results.trimToSize();

        final var last = results.getLast();
        lastKey = last.getKey();
      }

      return new SessionTableResponse(totalCount, lastKey, results);
    });
  }

  public Uni<Integer> expires(String id) {
    return repository().expires(id)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(id)));
  }
}
