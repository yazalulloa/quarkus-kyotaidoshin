package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.api.domain.response.permissions.PermissionCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.permissions.PermissionTableResponse;
import com.yaz.kyotaidoshin.core.service.cache.PermissionCache;
import com.yaz.kyotaidoshin.persistence.domain.PermissionQuery;
import com.yaz.kyotaidoshin.persistence.model.Permission;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.persistence.repository.PermissionRepository;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PermissionService {

  private final PermissionRepository repository;
  private final EncryptionService encryptionService;

  private PermissionRepository repository() {
    return repository;
  }

  @CacheInvalidateAll(cacheName = PermissionCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = PermissionCache.QUERY_COUNT)
  @CacheInvalidateAll(cacheName = PermissionCache.SELECT)
  @CacheInvalidateAll(cacheName = PermissionCache.SELECT_BY_USER)
//  @CacheInvalidate(cacheName = PermissionCache.EXISTS)
  public Uni<Void> invalidateAll() {
    return Uni.createFrom().voidItem();
  }

  public Uni<List<Permission>> list(PermissionQuery query) {
    return repository().select(query);
  }


  @CacheResult(cacheName = PermissionCache.SELECT_BY_USER, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<Permission>> selectByUser(String userId) {
    return repository().selectByUser(userId);
  }


  @CacheResult(cacheName = PermissionCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> totalCount() {
    return repository().count();
  }

  @CacheResult(cacheName = PermissionCache.QUERY_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Long>> queryCount(PermissionQuery query) {
    return repository().queryCount(query);
  }

  public Uni<PermissionCountersDto> counters(PermissionQuery apartmentQuery) {
    return Uni.combine()
        .all()
        .unis(totalCount(), queryCount(apartmentQuery))
        .with((totalCount, queryCount) -> new PermissionCountersDto(totalCount, queryCount.orElse(null)));

  }

  @CacheResult(cacheName = PermissionCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<PermissionTableResponse> search(PermissionQuery query) {

    final var actualLimit = (query.limit() == 0 ? 30 : query.limit()) + 1;

    final var permissionQuery = query.toBuilder()
        .limit(actualLimit)
        .build();

    return Uni.combine()
        .all()
        .unis(counters(permissionQuery), list(permissionQuery))
        .with((counters, permissions) -> {

          final var results = permissions.stream()
              .map(permission -> {
                final var keys = permission.keys();
                return PermissionTableResponse.Item.builder()
                    .key(encryptionService.encryptObj(keys))
                    .cardId(keys.cardId())
                    .item(permission)
                    .build();
              })
              .collect(Collectors.toCollection(() -> new ArrayList<>(permissions.size())));

          String lastKey = null;
          if (results.size() == actualLimit) {
            results.removeLast();
            results.trimToSize();

            final var last = results.getLast();
            lastKey = last.key();
          }

          return PermissionTableResponse.builder()
              .counters(counters)
              .results(results)
              .lastKey(lastKey)
              .build();
        });
  }

  public Uni<Integer> delete(String userId, String type) {
    return repository().delete(userId, type)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateAll()
                .replaceWith(i);
          }

          return Uni.createFrom().item(i);
        });
  }

  public Uni<Integer> insert(List<Pair<User, String>> pairs) {
    return repository().insert(pairs)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateAll()
                .replaceWith(i);
          }

          return Uni.createFrom().item(i);
        });
  }

  public Uni<Integer> update(String userId, Set<String> perms) {
    return repository().update(userId, perms)
        .flatMap(MutinyUtil.alwaysCall(invalidateAll()));
  }

  public Uni<Integer> deleteByUser(String userId) {
    return repository().deleteByUser(userId)
        .flatMap(MutinyUtil.alwaysCall(invalidateAll()));
  }
}
