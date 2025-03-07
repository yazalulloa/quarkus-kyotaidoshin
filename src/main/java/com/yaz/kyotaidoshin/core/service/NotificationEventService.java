package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.repository.NotificationEventRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class NotificationEventService {

  private final NotificationEventRepository repository;

  public Uni<List<NotificationEvent>> selectByUser(String userId) {
    return repository.selectByUser(userId);
  }

  public Uni<Integer> insert(String userId, NotificationEvent.Event event) {
    return repository.insert(userId, event);
  }

  public Uni<Integer> delete(String userId, NotificationEvent.Event event) {
    return repository.delete(userId, event);
  }

  public Uni<Integer> deleteByUser(String userId) {
    return repository.deleteByUser(userId);
  }
}
