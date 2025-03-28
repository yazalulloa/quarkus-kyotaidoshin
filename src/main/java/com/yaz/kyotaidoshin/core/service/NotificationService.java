package com.yaz.kyotaidoshin.core.service;


import com.yaz.kyotaidoshin.core.service.telegram.TelegramRestService;
import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.model.NotificationEvent.Event;
import com.yaz.kyotaidoshin.util.EnvParams;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class NotificationService {


  private final TelegramRestService restService;
  private final EnvParams envParams;

  //private final SendLogs sendLogs;
  private final TelegramChatService chatService;
//  private final TranslationProvider translationProvider;

  public void sendAppStartup() {
    final var event = NotificationEvent.Event.APP_STARTUP;
    final var msg = event.name();//translationProvider.translate(event.name());
    final var string = envParams.addEnvInfo(msg, true, false);

    send(string, Event.APP_STARTUP)
        .subscribe(() -> {
        }, throwable -> log.error("NOTIFICATION_ERROR", throwable));
  }


  public Completable sendNewRate(String msg) {
    return send(envParams.addEnvInfo(msg), NotificationEvent.Event.NEW_RATE);
  }

  public Completable send(String msg, NotificationEvent.Event event) {
    return sendNotification(chatId -> restService.rxSendMessage(chatId, msg), event);
  }

  private Completable sendNotification(LongFunction<Completable> function, NotificationEvent.Event... set) {
    if (!envParams.isSendNotifications()) {
      return Completable.complete();
    }

    return chatService.chatByEvents(set)
        .filter(s -> !s.isEmpty())
        .flatMapObservable(Observable::fromIterable)
        .map(function::apply)
        .toList()
        .toFlowable()
        .flatMapCompletable(Completable::merge);
  }

  public Completable sendShuttingDownApp() {
    final var event = NotificationEvent.Event.APP_SHUTTING_DOWN;
    final var caption = event.name();//translationProvider.translate(event.name());

    return send(envParams.addEnvInfo(caption), Event.APP_SHUTTING_DOWN);
  }


 /* private Completable sendNotification(Set<NotificationEvent> set, Function<Long, Completable> function) {
    return chatService.chatsByEvents(set)
        .filter(s -> !s.isEmpty())
        .flatMapObservable(Observable::fromIterable)
        .map(function::apply)
        .toList()
        .toFlowable()
        .flatMapCompletable(Completable::merge);
  }

  public Completable sendNewRate(String msg) {
    return send(EnvUtil.addEnvInfo(msg), NotificationEvent.NEW_RATE);
  }

  public Completable send(String msg, NotificationEvent event) {
    return sendNotification(Set.of(event), chat -> restService.sendMessage(chat, msg).ignoreElement());
  }*/

 /* private boolean blocking(Completable completable) {
    return completable
        .blockingAwait(10, TimeUnit.SECONDS);
  }*/

  /*public Completable sendLogs(long chatId, String caption) {
    return sendLogs.sendLogs(chatId, caption);
  }

  public boolean sendShuttingDownApp() {
    final var event = NotificationEvent.APP_SHUTTING_DOWN;
    final var caption = event.name();//translationProvider.translate(event.name());
    return blocking(sendNotification(Set.of(event), chat -> sendLogs(chat, EnvUtil.addEnvInfo(caption))));
  }*/
}
