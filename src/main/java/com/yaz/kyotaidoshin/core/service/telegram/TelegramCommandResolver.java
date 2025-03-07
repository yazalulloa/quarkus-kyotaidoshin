package com.yaz.kyotaidoshin.core.service.telegram;

import com.yaz.kyotaidoshin.core.client.telegram.CallbackQuery;
import com.yaz.kyotaidoshin.core.client.telegram.Chat;
import com.yaz.kyotaidoshin.core.client.telegram.EditMessageText;
import com.yaz.kyotaidoshin.core.client.telegram.InlineKeyboardButton;
import com.yaz.kyotaidoshin.core.client.telegram.InlineKeyboardMarkup;
import com.yaz.kyotaidoshin.core.client.telegram.ReplyParameters;
import com.yaz.kyotaidoshin.core.client.telegram.SendDocument;
import com.yaz.kyotaidoshin.core.client.telegram.SendMessage;
import com.yaz.kyotaidoshin.core.client.telegram.TelegramMessage;
import com.yaz.kyotaidoshin.core.client.telegram.TelegramUpdate;
import com.yaz.kyotaidoshin.core.client.telegram.TelegramUser;
import com.yaz.kyotaidoshin.core.service.RateService;
import com.yaz.kyotaidoshin.core.service.TelegramChatService;
import com.yaz.kyotaidoshin.core.service.UserService;
import com.yaz.kyotaidoshin.core.service.download.BackupDownloader;
import com.yaz.kyotaidoshin.persistence.model.TelegramChat;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.DateUtil;
import com.yaz.kyotaidoshin.util.EnvParams;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TelegramCommandResolver {

  private static final String LAST_RATE_MSG = "TASA: %s%nFECHA: %s%nCREADO: %s";

  private final UserService userService;
  private final RateService rateService;
  private final TelegramChatService chatService;
  private final TelegramRestService restService;
  private final EnvParams envParams;
  private final BackupDownloader backupDownloader;

  public void telegramMessageReceived(@ObservesAsync TelegramWebhookRequest task) {
    try {

      final var update = Json.decodeValue(task.body(), TelegramUpdate.class);
      resolve(update)
          .subscribe()
          .with(
              i -> {
              },
              e -> log.error("ERROR telegramMessageReceived: {}", task, e));
    } catch (Exception e) {
      log.error("telegramMessageReceived: {}", task, e);
    }

  }

  public Uni<Void> resolve(TelegramUpdate update) {

    return Uni.createFrom().deferred(() -> {

      return Uni.combine().all()
          .unis(command(update.message()), callbackQuery(update.callbackQuery()))
          .discardItems();
    });

  }

  public Uni<Void> command(TelegramMessage message) {

    if (message == null || message.entities() == null || message.entities().isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    final var entitiesItem = message.entities().getFirst();
    if (!entitiesItem.type().equals("bot_command")) {
      return Uni.createFrom().voidItem();
    }

    final var text = message.text();
    final var from = message.from();
    final var chat = message.chat();

    if (text == null || from == null || chat == null) {
      return Uni.createFrom().voidItem();
    }

    final var chatId = chat.id();

    if (text.startsWith("/start") && text.length() > 8 && !from.isBot()) {
      final var formatUserId = text.substring(7).trim();
      final var userId = ConvertUtil.getUserId(formatUserId);

      final var sendMessage = SendMessage.builder()
          .chatId(chatId)
          .replyParameters(ReplyParameters.builder()
              .messageId(message.messageId())
              .build())
          .build();

      return addAccount(userId, from, chat, sendMessage);
    }

    if (text.startsWith("/log")) {
      // return sendLogs.sendLogs(chatId, "logs");
    }

    if (text.startsWith("/system_info")) {

      final var msg = envParams.addEnvInfo("", true, true);
      final var sendMessage = SendMessage.builder()
          .chatId(chatId)
          .text(msg)
          .replyParameters(ReplyParameters.builder()
              .messageId(message.messageId())
              .build())
          .build();
      return restService.sendMessage(sendMessage)
          .replaceWithVoid();
    }

    if (text.startsWith("/tasa")) {

      return rateService.lastUni("USD", "VED")
          .map(opt -> opt.map(rate -> LAST_RATE_MSG.formatted(rate.rate(), rate.dateOfRate(),
                  DateUtil.formatVe(rate.createdAt().atZone(ZoneOffset.UTC))))
              .orElse("No hay tasa"))
          .map(msg -> SendMessage.builder()
              .chatId(chatId)
              .text(msg)
              .replyParameters(ReplyParameters.builder()
                  .messageId(message.messageId())
                  .build())
              .build())
          .flatMap(restService::sendMessage)
          .replaceWithVoid();
    }

    if (text.startsWith("/backups")) {
      return MutinyUtil.toUni(backupDownloader.all())
          .flatMap(path -> {

            final var sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(path.toFile())
                .replyParameters(ReplyParameters.replyTo(message.messageId()))
                .build();

            return restService.sendDocument(sendDocument);
          })
          .replaceWithVoid();
    }

    if (text.startsWith("/options")) {

      final var sendMessage = SendMessage.builder()
          .chatId(chatId)
          .text("Choose an option")
          .replyMarkup(firstMenuOptions())
          .replyParameters(ReplyParameters.builder()
              .messageId(message.messageId())
              .build())
          .build();

      return restService.sendMessage(sendMessage)
          .replaceWithVoid();
    }

    if (true) {
      final var sendMessage = SendMessage.builder()
          .chatId(chatId)
          .text("Comando no reconocido")
          .replyParameters(ReplyParameters.builder()
              .messageId(message.messageId())
              .build())
          .build();
      return restService.sendMessage(sendMessage)
          .replaceWithVoid();
    }

    return Uni.createFrom().voidItem();
  }

  private InlineKeyboardMarkup firstMenuOptions() {
    final var lists = Arrays.stream(BotOptions.OPTIONS)
        .map(Enum::name)
        .map(option -> InlineKeyboardButton.callbackData(option, option))
        .map(List::of)
        .toList();

    return InlineKeyboardMarkup.builder()
        .inlineKeyboard(lists)
        .build();
  }

  public Uni<Void> callbackQuery(CallbackQuery callbackQuery) {
    if (callbackQuery == null) {
      return Uni.createFrom().voidItem();
    }

    log.info("callbackQuery: {}", Json.encodePrettily(callbackQuery));

    final var data = callbackQuery.data();
    if (data != null) {

      if (data.equals("BACK_TO_OPTIONS")) {

        final var editMessageText = EditMessageText.builder()
            .chatId(callbackQuery.from().id())
            .text("Choose an options")
            .messageId(callbackQuery.message().messageId())
            .replyMarkup(firstMenuOptions())
            .build();

        return restService.editMessageText(editMessageText)
            .replaceWithVoid();
      }

      final var botOptions = BotOptions.option(data);
      if (botOptions != null) {
        switch (botOptions) {
          case LOGS -> {
            return restService.answerCallbackQuery(callbackQuery.id())
                .replaceWithVoid();
          }
          case RECEIPTS -> {
            return restService.answerCallbackQuery(callbackQuery.id())
                .replaceWithVoid();
          }
          case LAST_RATE -> {
            return rateService.lastUni("USD", "VED")
                .map(opt -> opt.map(rate -> LAST_RATE_MSG.formatted(rate.rate(), rate.dateOfRate(),
                        DateUtil.formatVe(rate.createdAt().atZone(ZoneOffset.UTC))))
                    .orElse("No hay tasa"))
                .map(msg -> SendMessage.builder()
                    .chatId(callbackQuery.from().id())
                    .text(msg)
                    .build())
                .flatMap(sendMessage -> {

                  return Uni.join()
                      .all(restService.sendMessage(sendMessage), restService.answerCallbackQuery(callbackQuery.id()))
                      .andCollectFailures();
                })
                .replaceWithVoid();
          }
          case BACKUPS -> {
            final var lists = new ArrayList<List<InlineKeyboardButton>>(BackupOptions.OPTIONS.length + 1);
            for (var option : BackupOptions.OPTIONS) {
              lists.add(
                  Collections.singletonList(InlineKeyboardButton.callbackData(option.name(), option.withPrefix())));
            }

            lists.add(Collections.singletonList(InlineKeyboardButton.callbackData("BACK", "BACK_TO_OPTIONS")));

            final var editMessageText = EditMessageText.builder()
                .chatId(callbackQuery.from().id())
                .text("Choose a backup")
                .messageId(callbackQuery.message().messageId())
                .replyMarkup(InlineKeyboardMarkup.builder()
                    .inlineKeyboard(lists)
                    .build())
                .build();

            return restService.editMessageText(editMessageText)
                .replaceWithVoid();
          }
          case SYSTEM_INFO -> {
            final var msg = envParams.addEnvInfo("", true, true);
            final var sendMessage = SendMessage.builder()
                .chatId(callbackQuery.from().id())
                .text(msg)
                .replyParameters(ReplyParameters.builder()
                    .build())
                .build();

            return Uni.join()
                .all(restService.sendMessage(sendMessage), restService.answerCallbackQuery(callbackQuery.id()))
                .andCollectFailures()
                .replaceWithVoid();
          }
        }

      }

      final var backupOptions = BackupOptions.option(data);
      if (backupOptions != null) {
        return MutinyUtil.toUni(backupDownloader.backup(backupOptions))
            .flatMap(path -> {

              final var sendDocument = SendDocument.builder()
                  .chatId(callbackQuery.from().id())
                  .document(path.toFile())
                  .build();

              return Uni.join()
                  .all(restService.sendDocument(sendDocument), restService.answerCallbackQuery(callbackQuery.id()))
                  .andCollectFailures();
            })
            .replaceWithVoid();

      }
    }

    //      if (callbackQuery.data().startsWith(TelegramSendEntityBackups.CALLBACK_KEY)) {
//        return sendEntityBackups.resolve(from.id(),
//            callbackQuery.data().replace(TelegramSendEntityBackups.CALLBACK_KEY, "").trim());
//      }

    return Uni.createFrom().voidItem();
  }


  private Uni<Void> addAccount(String userId, TelegramUser from, Chat chat, SendMessage sendMessage) {
    final var chatId = from.id();

    final var existsUni = userService.exists(userId);
    final var userUni = chatService.read(userId, chatId);

    return Uni.combine()
        .all()
        .unis(existsUni, userUni)
        .withUni((userExists, chatMaybe) -> {
          if (userExists) {

            final var data = new JsonObject()
                .put("from", new JsonObject(Json.encode(from)))
                .put("chat", new JsonObject(Json.encode(chat)));

            final var telegramChat = TelegramChat.builder()
                .userId(userId)
                .chatId(chatId)
                .firstName(from.firstName())
                .lastName(from.lastName())
                .username(from.username())
                .data(data)
                .build();

            if (chatMaybe.isEmpty()) {
              return chatService.save(telegramChat)
                  .replaceWith(restService.sendMessage(sendMessage.addText("Chat guardado")))
                  .replaceWithVoid();
            }

            return Uni.createFrom().deferred(() -> {
                  final var oldChat = chatMaybe.get();

                  final var equalsCheck = Objects.equals(telegramChat.firstName(), oldChat.firstName())
                      && Objects.equals(telegramChat.lastName(), oldChat.lastName())
                      && Objects.equals(telegramChat.username(), oldChat.username())
                      && Objects.equals(telegramChat.data(), oldChat.data());

                  if (!equalsCheck) {
                    return chatService.update(telegramChat).replaceWithVoid();
                  }

                  return Uni.createFrom().voidItem();
                })
                .replaceWith(restService.sendMessage(sendMessage.addText("Cuenta ya enlazada")))
                .replaceWithVoid();
          }

          return restService.sendMessage(sendMessage.addText("Usuario no encontrado"))
              .replaceWithVoid();

        });
  }
}
