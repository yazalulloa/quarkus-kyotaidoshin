package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.api.domain.response.UserTableResponse;
import com.yaz.kyotaidoshin.api.domain.response.UserTableResponse.Item;
import com.yaz.kyotaidoshin.api.domain.response.UserTableResponse.NotificationKey;
import com.yaz.kyotaidoshin.core.domain.events.UserDeletedEvent;
import com.yaz.kyotaidoshin.core.service.cache.UserCache;
import com.yaz.kyotaidoshin.persistence.domain.UserQuery;
import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import com.yaz.kyotaidoshin.persistence.repository.UserRepository;
import com.yaz.kyotaidoshin.util.Constants;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class UserService {

  //private final Instance<UserRepository> repository;
  private final EncryptionService encryptionService;
  private final UserRepository repository;
  private final Event<UserDeletedEvent> userDeletedEvent;


  private UserRepository repository() {
    //return repository.get();
    return repository;
  }

  public Uni<Long> count() {
    return repository().count();
  }

  @CacheInvalidate(cacheName = UserCache.GET_ID_FROM_PROVIDER)
  @CacheInvalidate(cacheName = UserCache.GET_FROM_PROVIDER)
  public Uni<Void> invalidateGetIdFromProvider(IdentityProvider provider, String providerId) {
    return Uni.createFrom().voidItem();
  }

  @CacheResult(cacheName = UserCache.GET_ID_FROM_PROVIDER, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<String>> getIdFromProvider(IdentityProvider provider, String providerId) {
    return repository().getIdFromProvider(provider, providerId);
  }

  @CacheResult(cacheName = UserCache.GET_FROM_PROVIDER, lockTimeout = Constants.CACHE_TIMEOUT)
  public Uni<Optional<User>> getFromProvider(IdentityProvider provider, String providerId) {
    return repository().getFromProvider(provider, providerId);
  }

  public Uni<String> saveIfExists(User user) {

    return repository().getIdFromProvider(user.provider(), user.providerId())
        .flatMap(optional -> {

          if (optional.isPresent()) {
            final var userId = optional.get();
            log.debug("User already exists {} {}", userId, user.providerId());
            return repository().updateLastLoginAt(userId)
                .replaceWith(invalidateGetIdFromProvider(user.provider(), user.providerId()))
                .replaceWith(userId);
          }

          return save(user);
        });
  }


  public Uni<String> save(User user) {
    return repository().save(user)
        .flatMap(id -> invalidateGetIdFromProvider(user.provider(), user.providerId()).replaceWith(id))
        .invoke(id -> log.debug("User inserted {}", id));
  }

  public Uni<List<User>> list(UserQuery userQuery) {
    return repository().select(userQuery);
  }

  public Uni<UserTableResponse> table(UserQuery userQuery) {
    final var actualLimit = userQuery.limit() + 1;
    return Uni.combine().all()
        .unis(count(), list(userQuery.toBuilder()
            .limit(actualLimit)
            .build()))
        .with((totalCount, list) -> {
          final var results = list.stream()
              .map(user -> {
                final var keys = user.keys();
                final var key = encryptionService.encryptObj(keys);

                final var notificationKeys = Arrays.stream(NotificationEvent.Event.VALUES).map(event -> {

                  final var notificationKey = new NotificationKey(user.id(), event);
                  final var notificationKeyStr = encryptionService.encryptObj(notificationKey);

                  return new NotificationKey(notificationKeyStr, event);
                }).toList();

                return Item.builder()
                    .key(key)
                    .cardId(keys.cardId())
                    .user(user)
                    .notificationKeys(notificationKeys)
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

          return UserTableResponse.builder()
              .totalCount(totalCount)
              .lastKey(lastKey)
              .results(results)
              .build();
        });
  }

  @CacheInvalidateAll(cacheName = UserCache.GET_ID_FROM_PROVIDER)
  public Uni<Integer> delete(String id) {
    return repository().delete(id)
        .eventually(() -> invalidateGetIdFromProvider(null, null))
        .onItem()
        .invoke(() -> userDeletedEvent.fireAsync(new UserDeletedEvent(id)));
  }

  public Uni<Optional<User>> read(String userId) {
    return repository().read(userId);
  }

  public Uni<Boolean> exists(String userId) {
    return repository().exists(userId);
  }

  public Uni<Integer> update(User user) {
    return repository().update(user);
  }

  public Uni<List<User>> all() {
    return list(UserQuery.builder().limit(1000).build());
  }
}
