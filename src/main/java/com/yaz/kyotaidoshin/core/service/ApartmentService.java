package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.api.domain.request.ApartmentRequest;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.apartments.ApartmentTableResponse;
import com.yaz.kyotaidoshin.core.service.cache.ApartmentCache;
import com.yaz.kyotaidoshin.core.service.domain.FileResponse;
import com.yaz.kyotaidoshin.core.service.download.WriteEntityToFile;
import com.yaz.kyotaidoshin.persistence.domain.ApartmentQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.model.Apartment;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.repository.ApartmentRepository;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.ListService;
import com.yaz.kyotaidoshin.util.ListServicePagingProcessorImpl;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingProcessor;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.reactivex.rxjava3.core.Single;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ApartmentService {


  //private final Instance<ApartmentRepository> repository;
  private final ApartmentRepository repository;
  private final EncryptionService encryptionService;
  private final Event<Apartment.Keys> aptDeletedEvent;
  private final WriteEntityToFile writeEntityToFile;


  private ApartmentRepository repository() {
    //return repository.get();
    return repository;
  }

  public Uni<Integer> delete(Apartment.Keys keys) {
    final var buildingId = keys.buildingId();
    final var number = keys.number();
    return repository().delete(buildingId, number)
        .flatMap(MutinyUtil.cacheCall(invalidateOne(buildingId, number)))
        .onItem()
        .invoke(() -> aptDeletedEvent.fireAsync(keys));
  }

  @CacheResult(cacheName = ApartmentCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> totalCount() {
    return repository().count();
  }

  @CacheResult(cacheName = ApartmentCache.QUERY_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Long>> queryCount(ApartmentQuery query) {
    return repository().queryCount(query);
  }

  public Uni<List<Apartment>> list(ApartmentQuery query) {
    return repository().select(query);
  }

  @CacheResult(cacheName = ApartmentCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<ApartmentTableResponse> tableResponse(ApartmentQuery query) {

    final var actualLimit = query.limit() + 1;

    final var apartmentQuery = query.toBuilder()
        .limit(actualLimit)
        .build();

    return Uni.combine()
        .all()
        .unis(counters(apartmentQuery), list(apartmentQuery))
        .with((counters, apartments) -> {

          final var results = apartments.stream()
              .map(apartment -> {
                final var keys = apartment.keys();
                return ApartmentTableResponse.Item.builder()
                    .key(encryptionService.encryptObj(keys))
                    .cardId(keys.cardId())
                    .item(apartment)
                    .build();
              })
              .collect(Collectors.toCollection(() -> new ArrayList<>(apartments.size())));

          String lastKey = null;
          if (results.size() == actualLimit) {
            results.removeLast();
            results.trimToSize();

            final var last = results.getLast();
            lastKey = last.key();
          }

          return ApartmentTableResponse.builder()
              .countersDto(counters)
              .results(results)
              .lastKey(lastKey)
              .q(query.q())
              .buildings(query.buildings())
              .build();
        });
  }

  @CacheResult(cacheName = ApartmentCache.EXISTS, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Boolean> exists(String buildingId, String number) {
    return repository().exists(buildingId, number);
  }


  @CacheInvalidateAll(cacheName = ApartmentCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = ApartmentCache.QUERY_COUNT)
  @CacheInvalidateAll(cacheName = ApartmentCache.SELECT_MINIMAL_BY_BUILDINGS)
  @CacheInvalidate(cacheName = ApartmentCache.EXISTS)
  public Uni<Void> invalidateOne(String buildingId, String number) {
    return invalidateGet(buildingId, number);
  }

  @CacheInvalidateAll(cacheName = ApartmentCache.SELECT)
  @CacheInvalidate(cacheName = ApartmentCache.GET)
  public Uni<Void> invalidateGet(String buildingId, String number) {
    return Uni.createFrom().voidItem();
  }


  public Uni<Apartment> create(ApartmentRequest request) {
    final var apartment = Apartment.builder()
        .buildingId(request.getBuildingId())
        .number(request.getNumber())
        .name(request.getName())
        .aliquot(request.getAliquot())
        .emails(request.getEmails())
        .build();

    return insert(apartment);
  }

  public Uni<Apartment> insert(Apartment apartment) {

    return repository().insert(apartment)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateOne(apartment.buildingId(), apartment.number())
                .replaceWith(apartment);
          }

          return Uni.createFrom().item(apartment);
        });
  }

  public Uni<ApartmentCountersDto> counters(ApartmentQuery apartmentQuery) {
    return Uni.combine()
        .all()
        .unis(totalCount(), queryCount(apartmentQuery))
        .with((totalCount, queryCount) -> new ApartmentCountersDto(totalCount, queryCount.orElse(null)));

  }

  @CacheResult(cacheName = ApartmentCache.GET, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Apartment>> get(String buildingId, String number) {
    return repository().read(buildingId, number);
  }


  public Uni<Integer> update(Apartment apartment) {

    return repository().update(apartment)
        .flatMap(MutinyUtil.cacheCall(invalidateGet(apartment.buildingId(), apartment.number())));
  }

  @CacheResult(cacheName = ApartmentCache.SELECT_MINIMAL_BY_BUILDINGS, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<ExtraCharge.Apt>> aptByBuildings(String buildingId) {
    return repository().aptByBuildings(buildingId);
  }

  public Uni<List<Apartment>> apartmentsByBuilding(String buildingId) {
    return repository().apartmentsByBuilding(buildingId);
  }

  public PagingProcessor<List<Apartment>> pagingProcessor(int pageSize, SortOrder sortOrder) {
    return new ListServicePagingProcessorImpl<>(new ApartmentListService(this),
        ApartmentQuery.builder().limit(pageSize).build());
  }

  public Single<FileResponse> downloadFile() {
    return writeEntityToFile.downloadFile("apartments.json.gz", pagingProcessor(100, SortOrder.ASC));
  }

  private record ApartmentListService(ApartmentService service) implements
      ListService<Apartment, ApartmentQuery> {

    @Override
    public Single<List<Apartment>> listByQuery(ApartmentQuery query) {
      return MutinyUtil.single(service.list(query));
    }

    @Override
    public ApartmentQuery nextQuery(List<Apartment> list, ApartmentQuery query) {
      if (list.isEmpty()) {
        return query;
      }

      return query.toBuilder()
          .lastBuildingId(list.getLast().buildingId())
          .lastNumber(list.getLast().number())
          .build();
    }
  }

  public Uni<Integer> insert(Collection<Apartment> apartments) {
    return repository().insert(apartments)
        .flatMap(MutinyUtil.cacheCall(invalidateOne("", "")));
  }
}
