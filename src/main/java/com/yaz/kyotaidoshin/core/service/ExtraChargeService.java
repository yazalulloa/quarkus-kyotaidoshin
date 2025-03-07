package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.core.service.cache.ExtraChargesCache;
import com.yaz.kyotaidoshin.persistence.domain.ExtraChargeCreateRequest;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.repository.ExtraChargeRepository;
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
public class ExtraChargeService {

  private final ExtraChargeRepository repository;

  @CacheInvalidateAll(cacheName = ExtraChargesCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = ExtraChargesCache.QUERY_COUNT)
  @CacheInvalidate(cacheName = ExtraChargesCache.EXISTS)
  public Uni<Void> invalidateOne(long id) {
    return invalidateGet(id);
  }

  @CacheInvalidateAll(cacheName = ExtraChargesCache.SELECT)
  @CacheInvalidate(cacheName = ExtraChargesCache.GET)
  public Uni<Void> invalidateGet(long id) {
    return Uni.createFrom().voidItem();
  }

  @CacheInvalidateAll(cacheName = ExtraChargesCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = ExtraChargesCache.QUERY_COUNT)
  @CacheInvalidateAll(cacheName = ExtraChargesCache.EXISTS)
  @CacheInvalidateAll(cacheName = ExtraChargesCache.SELECT)
  @CacheInvalidateAll(cacheName = ExtraChargesCache.GET)
  public Uni<Void> invalidateAll() {
    return Uni.createFrom().voidItem();
  }

  @CacheResult(cacheName = ExtraChargesCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count() {
    return repository.count();
  }


  public Uni<Long> count(ExtraCharge.Keys keys) {
    final var receiptCount = Uni.createFrom().deferred(() -> {
      if (keys.receiptId() != null) {
        return count(keys.buildingId(), keys.receiptId());
      }
      return Uni.createFrom().item(0L);
    });

    return Uni.combine().all()
        .unis(count(keys.buildingId(), keys.buildingId()), receiptCount)
        .with(Long::sum);
  }

  @CacheResult(cacheName = ExtraChargesCache.QUERY_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count(String buildingId, String parentReference) {
    return repository.count(buildingId, parentReference);
  }

  public Uni<Optional<ExtraCharge>> read(ExtraCharge.Keys keys) {
    return read(keys.buildingId(), keys.parentReference(), keys.id());
  }

  @CacheResult(cacheName = ExtraChargesCache.GET, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<ExtraCharge>> read(String buildingId, String parentReference, long id) {
    return repository.read(buildingId, parentReference, id);
  }

  @CacheResult(cacheName = ExtraChargesCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<ExtraCharge>> by(String buildingId, String parentReference) {
    return repository.select(buildingId, parentReference);
  }


  public Uni<Integer> delete(ExtraCharge.Keys keys) {
    return repository.delete(keys.parentReference(), keys.buildingId(), keys.id())
        .flatMap(MutinyUtil.cacheCall(invalidateOne(keys.id())));
  }

  public Uni<ExtraCharge> create(ExtraChargeCreateRequest createRequest) {

    final var extraCharge = ExtraCharge.builder()
        .parentReference(createRequest.parentReference())
        .buildingId(createRequest.buildingId())
        .type(createRequest.type())
        .description(createRequest.description())
        .amount(createRequest.amount())
        .currency(createRequest.currency())
        .active(createRequest.active())
        .build();

    return repository.insert(extraCharge, createRequest.apartments())
        .map(res -> extraCharge.toBuilder()
            .id(res.id())
            .build())
        .flatMap(e -> invalidateOne(e.id())
            .replaceWith(e));
  }

  public Uni<Integer> update(ExtraCharge extraCharge) {
    return repository.update(extraCharge)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(extraCharge.id())));
  }

  public Uni<Integer> deleteByBuilding(String id) {
    return repository.deleteByBuilding(id)
        .flatMap(MutinyUtil.cacheCall(invalidateAll()));
  }


  public Uni<Integer> deleteByReceipt(String buildingId, String parentReference) {
    return repository.deleteByReceipt(buildingId, parentReference)
        .flatMap(MutinyUtil.cacheCall(invalidateAll()));
  }

  public Uni<Integer> deleteByApartment(String buildingId, String aptNumber) {
    return repository.deleteByApartment(buildingId, aptNumber)
        .flatMap(MutinyUtil.cacheCall(invalidateAll()));
  }

  public Uni<Integer> insertBulk(List<ExtraCharge> extraCharges) {
    return repository.insertBulk(extraCharges)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(0)));
  }
}
