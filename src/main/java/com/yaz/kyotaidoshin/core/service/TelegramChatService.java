package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.model.TelegramChat;
import com.yaz.kyotaidoshin.persistence.repository.TelegramChatRepository;
import com.yaz.kyotaidoshin.util.RxUtil;
import io.reactivex.rxjava3.core.Single;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TelegramChatService {

  private final TelegramChatRepository repository;

  public Uni<Integer> deleteByUser(String userId) {
    return repository.deleteByUser(userId);
  }

  public Uni<Integer> delete(String userId, long chatId) {
    return repository.delete(userId, chatId);
  }

  public Uni<Integer> save(TelegramChat chat) {
    return repository.save(chat);
  }

  public Uni<Optional<TelegramChat>> read(String userId, long chatId) {
    return repository.read(userId, chatId);
  }

  public Uni<Boolean> exists(String userId, long chatId) {
    return repository.exists(userId, chatId);
  }

  public Uni<Integer> update(TelegramChat telegramChat) {
    return repository.update(telegramChat);
  }

  public Single<Set<Long>> chatByEvents(NotificationEvent.Event... events) {
    return RxUtil.single(repository.chatByEvents(events));
  }
}
