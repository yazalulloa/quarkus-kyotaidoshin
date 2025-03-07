package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.api.domain.response.rate.RateCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.rate.RateTableResponse;
import com.yaz.kyotaidoshin.core.service.cache.RateCache;
import com.yaz.kyotaidoshin.core.service.domain.FileResponse;
import com.yaz.kyotaidoshin.core.service.download.WriteEntityToFile;
import com.yaz.kyotaidoshin.persistence.domain.RateQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.repository.RateRepository;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.ListService;
import com.yaz.kyotaidoshin.util.ListServicePagingProcessorImpl;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingProcessor;
import com.yaz.kyotaidoshin.util.RxUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RateService {


  //private final Instance<RateRepository> repository;
  private final RateRepository repository;
  private final WriteEntityToFile writeEntityToFile;
  private final EncryptionService encryptionService;
//  private final BcvHistoricService bcvHistoricService;

  private RateRepository repository() {
    //return repository.get();
    return repository;
  }

  public Uni<RateCountersDto> counters(String date, Set<String> currencies, SortOrder sortOrder) {
    final var rateQuery = RateQuery.builder()
        .date(StringUtil.validLocalDate(date))
        .currencies(currencies)
        .sortOrder(sortOrder)
        .build();

    return Uni.combine().all()
        .unis(count(), queryCount(rateQuery))
        .with((count, opt) -> new RateCountersDto(opt.orElse(null), count));
  }

  @CacheResult(cacheName = RateCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count() {
    return repository().count();
  }

  @CacheResult(cacheName = RateCache.QUERY_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Long>> queryCount(RateQuery rateQuery) {
    if (rateQuery.date() != null || (rateQuery.currencies() != null && !rateQuery.currencies().isEmpty())) {
      return repository().queryCount(rateQuery);
    }

    return Uni.createFrom().item(Optional.empty());
  }

  public Uni<Integer> delete(long id) {
    return repository().delete(id)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateOne(id)
                .replaceWith(i);
          }

          return Uni.createFrom().item(i);
        });
  }

  @CacheInvalidateAll(cacheName = RateCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = RateCache.QUERY_COUNT)
  @CacheInvalidate(cacheName = RateCache.EXISTS)
  public Uni<Void> invalidateOne(long id) {
    return invalidateGet(id);
  }

  @CacheInvalidateAll(cacheName = RateCache.LAST)
  @CacheInvalidateAll(cacheName = RateCache.SELECT)
  @CacheInvalidate(cacheName = RateCache.GET)
  public Uni<Void> invalidateGet(long id) {
    return Uni.createFrom().voidItem();
  }

  @CacheResult(cacheName = RateCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<Rate>> list(RateQuery rateQuery) {
    return repository().listRows(rateQuery);
  }

  @CacheResult(cacheName = RateCache.LAST, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Rate>> lastUni(String fromCurrency, String toCurrency) {
    return repository().last(fromCurrency, toCurrency);
  }

  public Maybe<Rate> last(String fromCurrency, String toCurrency) {
    return RxUtil.toMaybe(lastUni(fromCurrency, toCurrency))
        .flatMap(Maybe::fromOptional);
  }


  public Uni<Rate> save(Rate rate) {

    return repository().save(rate)
        .map(id -> {
          return rate.toBuilder()
              .id(id.orElse(null))
              .build();
        })
        .flatMap(newRate -> {
          if (newRate.id() == null) {
            return Uni.createFrom().item(newRate);
          }

          return invalidateOne(newRate.id())
              .replaceWith(newRate);
        });
  }

//  @CacheResult(cacheName = RateCache.EXISTS, lockTimeout = Constants.CACHE_TIMEOUT)
//  public Uni<Boolean> existsUni(long hash) {
//    return repository().exists(hash);
//  }
//
//  public Single<Boolean> exists(Rate rate) {
//    return Single.mergeArray(RxUtil.single(existsUni(rate.hash())), exists(rate.rate(), rate.dateOfRate()))
//        .reduce(Boolean::logicalOr)
//        .defaultIfEmpty(false);
//  }
//
//  public Single<Boolean> exists(BigDecimal rate, LocalDate dateOfRate) {
//    return RxUtil.single(repository().exists(rate, dateOfRate));
//  }

  public Uni<Boolean> exists(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate dateOfRate) {
    return repository().exists(fromCurrency, toCurrency, rate, dateOfRate);
  }

 /* private Single<HttpResponse<Buffer>> bcv(HttpMethod httpMethod) {
    return httpService.bcv(httpMethod);
  }*/

//  private Single<BcvUsdRateResult> newRateResult() {
//
//    return //bcvClientService.currentRate()
//        bcvHistoricService.currentRate()
//            .map(newRate -> new BcvUsdRateResult(BcvUsdRateResult.State.NEW_RATE, newRate))
//            .subscribeOn(Schedulers.io());
//  }

  @CacheResult(cacheName = RateCache.GET, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Rate>> read(long id) {
    return repository().read(id);
  }

  public Uni<Rate> get(long id) {
    return read(id)
        .map(optional -> optional.orElseThrow(() -> new IllegalArgumentException("Rate not found: " + id)));
  }

  public Uni<List<String>> currencies() {
    return repository().currencies();
  }

//  private Maybe<BcvUsdRateResult> bcvCheck(Rate rate) {
//    return //bcvClientService.bcvCheck()
//        bcvHistoricService.bcvCheck()
//            .filter(bcvCheck -> Objects.equals(bcvCheck.etag(), rate.etag()))
//            .map(b -> new BcvUsdRateResult(BcvUsdRateResult.State.ETAG_IS_SAME));
//  }
//
//  public Single<BcvUsdRateResult> newRate() {
//
//    return last(Currency.USD, Currency.VED)
//        .filter(rate -> rate.etag() != null)
//        .flatMap(this::bcvCheck)
//        .switchIfEmpty(Single.defer(this::newRateResult));
//  }


  public Uni<RateTableResponse> table(RateQuery rateQuery) {
    final var actualLimit = rateQuery.limit() + 1;
    return Uni.combine().all()
        .unis(count(), queryCount(rateQuery), list(rateQuery.toBuilder()
            .limit(actualLimit)
            .build()))
        .with((totalCount, queryCount, list) -> {
          final var results = list.stream()
              .map(rate -> {

                final var keys = rate.keys();
                final var key = encryptionService.encryptObj(keys);
                return RateTableResponse.Item.builder()
                    .key(key)
                    .item(rate)
                    .cardId(keys.cardId())
                    .build();
              })
              .collect(Collectors.toCollection(() -> new ArrayList<>(list.size())));

          String lastKey = null;
          if (results.size() == actualLimit) {
            results.removeLast();
            results.trimToSize();

            final var last = results.getLast();
            lastKey = last.key();
          }

          return RateTableResponse.builder()
              .lastKey(lastKey)
              .results(results)
              .countersDto(RateCountersDto.builder()
                  .totalCount(totalCount)
                  .queryCount(queryCount.orElse(null))
                  .build())
              .date(rateQuery.date())
              .currencies(rateQuery.currencies())
              .build();
        });
  }

  public Uni<Integer> insert(Collection<Rate> rates) {
    if (rates.isEmpty()) {
      return Uni.createFrom().item(0);
    }

    return repository().insert(rates)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateOne(0)
                .replaceWith(i);
          }

          return Uni.createFrom().item(i);
        });
  }

  public PagingProcessor<List<Rate>> pagingProcessor(int pageSize, SortOrder sortOrder) {
    return new ListServicePagingProcessorImpl<>(new RateListService(this), RateQuery.query(pageSize, sortOrder));
  }

  public Single<FileResponse> downloadFile() {
    return writeEntityToFile.downloadFile("rates.json.gz", pagingProcessor(100, SortOrder.ASC));
  }

  private record RateListService(RateService rateService) implements ListService<Rate, RateQuery> {

    @Override
    public Single<List<Rate>> listByQuery(RateQuery query) {
      return MutinyUtil.single(rateService.list(query));
    }

    @Override
    public RateQuery nextQuery(List<Rate> list, RateQuery query) {
      if (list.isEmpty()) {
        return query;
      }

      return query.toBuilder()
          .lastId(list.getLast().id())
          .build();
    }
  }
}
