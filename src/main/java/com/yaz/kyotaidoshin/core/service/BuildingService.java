package com.yaz.kyotaidoshin.core.service;


import com.yaz.kyotaidoshin.api.domain.response.building.BuildingTableResponse;
import com.yaz.kyotaidoshin.core.domain.events.BuildingDeleted;
import com.yaz.kyotaidoshin.core.service.cache.BuildingCache;
import com.yaz.kyotaidoshin.core.service.mailer.MailerService;
import com.yaz.kyotaidoshin.persistence.domain.BuildingQuery;
import com.yaz.kyotaidoshin.persistence.model.Building;
import com.yaz.kyotaidoshin.persistence.repository.BuildingRepository;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.runtime.ExecutorRecorder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
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
public class BuildingService {

//  private static final String dirPath = StaticReactiveRoutes.TMP_STATIC_PATH + "buildings/ids/";

  private static String filePath;

  //private final Instance<BuildingRepository> repository;
  private final BuildingRepository repository;
  private final EncryptionService encryptionService;
  private final Event<BuildingDeleted> buildingDeletedEvent;
  private final MailerService mailerService;
  private final Vertx vertx;

  private BuildingRepository repository() {
    //return repository.get();
    return repository;
  }

  @CacheResult(cacheName = BuildingCache.TOTAL_COUNT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Long> count() {
    return repository().count();
  }


  public Uni<Integer> delete(String id) {
    return repository().delete(id)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateOne(id)
                .replaceWith(i)
                .eventually(this::clearIdList);
          }

          return Uni.createFrom().item(i);
        })
        .eventually(() -> Uni.createFrom().completionStage(buildingDeletedEvent.fireAsync(new BuildingDeleted(id))));
  }

  @CacheInvalidateAll(cacheName = BuildingCache.TOTAL_COUNT)
  @CacheInvalidateAll(cacheName = BuildingCache.QUERY_COUNT)
  @CacheInvalidateAll(cacheName = BuildingCache.IDS)
  @CacheInvalidate(cacheName = BuildingCache.EXISTS)
  public Uni<Void> invalidateOne(String id) {
    return invalidateGet(id);
  }

  @CacheInvalidateAll(cacheName = BuildingCache.SELECT)
  @CacheInvalidate(cacheName = BuildingCache.GET)
  public Uni<Void> invalidateGet(String id) {
    return Uni.createFrom().voidItem();
  }

  private void clearIdList() {
    if (filePath != null) {
      final var pathToDelete = filePath;
      filePath = null;

      vertx.fileSystem().delete(pathToDelete)
//      vertx.fileSystem().exists(dirPath)
//          .flatMap(b -> {
//            if (!b) {
//              return Uni.createFrom().voidItem();
//            }
//
//            return vertx.fileSystem().readDir(dirPath)
//                .toMulti()
//                .flatMap(Multi.createFrom()::iterable)
//                .onItem()
//                .transformToUni(str -> vertx.fileSystem().delete(str))
//                .merge()
//                .toUni();
//          })
          .runSubscriptionOn(ExecutorRecorder.getCurrent())
//          .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
          .subscribe()
          .with(v -> {
            //log.info("Deleted file: {}", filePath);
          }, t -> {
            log.error("Error deleting file: {}", filePath, t);
          });
    }
  }


  @CacheResult(cacheName = BuildingCache.SELECT, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<Building>> list(BuildingQuery buildingQuery) {
    return repository().select(buildingQuery);
  }

  @CacheResult(cacheName = BuildingCache.IDS, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<List<String>> ids() {
    return repository().selectAllIds();
  }

  @CacheResult(cacheName = BuildingCache.EXISTS, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Boolean> exists(String buildingId) {
    return repository().exists(buildingId);
  }

  /*public Uni<String> buildIds() {
    final var createUni = ids()
        .map(Templates::ids)
        .flatMap(TemplateInstance::createUni)
        .flatMap(str -> {
          return vertx.fileSystem().mkdirs(dirPath)
              .flatMap(v -> vertx.fileSystem().createFile(filePath))
              .flatMap(v -> vertx.fileSystem().writeFile(filePath, Buffer.buffer(str)))
              //.invoke(v -> log.info("Create file {} \n {}", filePath, str))
              ;
        });

    if (filePath == null) {
      filePath = "%sids-%s.html".formatted(dirPath, RandomUtil.randomStr(6));
      return createUni.replaceWith(filePath);
    }

    return vertx.fileSystem().exists(filePath)
        .flatMap(bool -> {
          if (bool) {
            return Uni.createFrom().voidItem();
          } else {

            return ids()
                .map(Templates::ids)
                .flatMap(TemplateInstance::createUni)
                .flatMap(str -> {
                  return vertx.fileSystem().mkdirs(dirPath)
                      .flatMap(v -> vertx.fileSystem().createFile(filePath))
                      .flatMap(v -> vertx.fileSystem().writeFile(filePath, Buffer.buffer(str)))
                      //.invoke(v -> log.info("Create file {} \n {}", filePath, str))
                      ;
                });

          }
        })
        .onFailure()
        .retry()
        .withBackOff(Duration.ofMillis(600))
        .atMost(3)
        .replaceWith(filePath);
  }*/

  public Uni<BuildingTableResponse> report(BuildingQuery buildingQuery) {
    final var actualLimit = buildingQuery.limit() + 1;
    final var query = buildingQuery.toBuilder()
        .limit(actualLimit)
        .build();

    return Uni.combine().all()
        .unis(count(), list(query))
        .with((totalCount, list) -> {
          final var results = list.stream()
              .map(building -> {
                final var keys = building.keys();
                return BuildingTableResponse.Item.builder()
                    .key(encryptionService.encryptObj(keys))
                    .item(building.toBuilder()
                        .configEmail(mailerService.email(building.emailConfigId()))
                        .build())
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

          return BuildingTableResponse.builder()
              .totalCount(totalCount)
              .lastKey(lastKey)
              .results(results)
              .build();
        });
  }

  @CacheResult(cacheName = BuildingCache.GET, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<Building>> read(String buildingId) {
    return repository().read(buildingId);
  }

  public Uni<Building> get(String buildingId) {
    return read(buildingId)
        .map(optional -> optional.orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId)));
  }


  public Uni<Building> update(Building building) {
    return repository().update(building)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateGet(building.id())
                .replaceWith(building);
          }

          return Uni.createFrom().item(building);
        });
  }

  public Uni<Building> create(Building building) {

    return repository().insert(building)
        .flatMap(i -> {
          if (i > 0) {
            return invalidateOne(building.id())
                .replaceWith(building)
                .eventually(this::clearIdList);
          }

          return Uni.createFrom().item(building);
        });
  }

  public Uni<Integer> insertIgnore(Collection<Building> buildings) {
    return repository().insertIgnore(buildings)
        .flatMap(MutinyUtil.cacheCall(invalidateOne("")));
  }

  public Uni<Integer> updateEmailConfig(String id) {
    return repository().selectByEmailConfig(id)
        .flatMap(set -> {
          if (set.isEmpty()) {
            return Uni.createFrom().item(0);
          }

          return repository().updateEmailConfig(set)
              .flatMap(i -> {
                return Multi.createFrom().iterable(set)
                    .toUni()
                    .flatMap(this::invalidateOne)
                    .replaceWith(i);
              });

        });
  }

  public Uni<Integer> updateEmailConfig(String oldConfigId, String newConfigId) {
    return repository().selectByEmailConfig(oldConfigId)
        .flatMap(set -> {
          if (set.isEmpty()) {
            return Uni.createFrom().item(0);
          }

          return repository().updateEmailConfig(set, newConfigId)
              .flatMap(i -> {
                return Multi.createFrom().iterable(set)
                    .toUni()
                    .flatMap(this::invalidateOne)
                    .replaceWith(i);
              });

        });
  }
}
