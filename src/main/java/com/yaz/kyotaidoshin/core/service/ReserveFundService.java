package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.core.service.cache.ReserveFundCache;
import com.yaz.kyotaidoshin.persistence.model.ReserveFund;
import com.yaz.kyotaidoshin.persistence.repository.ReserveFundRepository;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ReserveFundService {

  private final ReserveFundRepository repository;

  @CacheInvalidateAll(cacheName = ReserveFundCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = ReserveFundCache.BUILDING_COUNT)
  @CacheInvalidate(cacheName = ReserveFundCache.EXISTS)
  public Uni<Void> invalidateOne(String buildingId, long id) {
    return invalidateGet(buildingId, id);
  }

  @CacheInvalidateAll(cacheName = ReserveFundCache.SELECT)
  @CacheInvalidate(cacheName = ReserveFundCache.GET)
  public Uni<Void> invalidateGet(String buildingId, long id) {
    return Uni.createFrom().voidItem();
  }

  @CacheInvalidateAll(cacheName = ReserveFundCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = ReserveFundCache.BUILDING_COUNT)
  @CacheInvalidateAll(cacheName = ReserveFundCache.EXISTS)
  @CacheInvalidateAll(cacheName = ReserveFundCache.SELECT)
  @CacheInvalidateAll(cacheName = ReserveFundCache.GET)
  public Uni<Void> invalidateAll() {
    return Uni.createFrom().voidItem();
  }

  @CacheResult(cacheName = ReserveFundCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count() {
    return repository.count();
  }

  @CacheResult(cacheName = ReserveFundCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<ReserveFund>> listByBuilding(String buildingId) {
    return repository.selectByBuilding(buildingId);
  }


  @CacheResult(cacheName = ReserveFundCache.GET, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<ReserveFund>> read(String buildingId, long id) {
    return repository.read(buildingId, id);
  }


  @CacheResult(cacheName = ReserveFundCache.BUILDING_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count(String buildingId) {
    return repository.count(buildingId);
  }

  public Uni<Integer> delete(String buildingId, long id) {
    return repository.delete(buildingId, id)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(buildingId, id)));
  }

  public Uni<Integer> deleteByBuilding(String buildingId) {
    return repository.deleteByBuilding(buildingId)
        .flatMap(MutinyUtil.cacheCall(invalidateAll()));
  }

  public Uni<Long> insert(ReserveFund reserveFund) {
    return repository.insert(reserveFund)
        .flatMap(id -> invalidateOne(reserveFund.buildingId(), id)
            .replaceWith(id));
  }

  public Uni<Integer> update(ReserveFund reserveFund) {
    return repository.update(reserveFund)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(reserveFund.buildingId(), reserveFund.id())));
  }

  public Uni<Integer> insertBulk(List<ReserveFund> reserveFunds) {
    return repository.insertBulk(reserveFunds)
        .flatMap(MutinyUtil.cacheCall(invalidateOne("", 0)));
  }
}
