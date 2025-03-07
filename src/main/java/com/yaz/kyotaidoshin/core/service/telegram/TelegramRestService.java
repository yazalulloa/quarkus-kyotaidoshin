package com.yaz.kyotaidoshin.core.service.telegram;

import com.yaz.kyotaidoshin.core.client.TelegramClient;
import com.yaz.kyotaidoshin.core.client.TelegramClient.GetUpdatesRequest;
import com.yaz.kyotaidoshin.core.client.TelegramClient.TelegramUpdateResponse;
import com.yaz.kyotaidoshin.core.client.telegram.AnswerCallbackQuery;
import com.yaz.kyotaidoshin.core.client.telegram.EditMessageText;
import com.yaz.kyotaidoshin.core.client.telegram.ParseMode;
import com.yaz.kyotaidoshin.core.client.telegram.SendDocument;
import com.yaz.kyotaidoshin.core.client.telegram.SendMessage;
import com.yaz.kyotaidoshin.core.client.telegram.TelegramUpdate;
import com.yaz.kyotaidoshin.core.client.telegram.WebHookRequest;
import com.yaz.kyotaidoshin.util.RandomUtil;
import com.yaz.kyotaidoshin.util.RxUtil;
import io.reactivex.rxjava3.core.Completable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

@Slf4j
@ApplicationScoped
public class TelegramRestService {

  private final TelegramClient client;

  @Inject
  public TelegramRestService(@RestClient TelegramClient client) {
    this.client = client;
  }

  private Uni<String> request(SendMessage sendMessage) {

    final var build = sendMessage.toBuilder()
        .parseMode(Objects.requireNonNullElse(sendMessage.parseMode(), ParseMode.HTML))
        .build();

    return client.sendMessage(build);
  }

  public Uni<String> sendDocument(SendDocument sendDocument) {

    if (true) {
      return client.sendDocument(sendDocument);

    }

    final var multiPartForm = ClientMultipartForm.create();
    multiPartForm.attribute("chat_id", String.valueOf(sendDocument.chatId()), null);
    if (sendDocument.replyParameters() != null) {
      multiPartForm.entity("reply_parameters", sendDocument.replyParameters(), MediaType.APPLICATION_JSON,
          sendDocument.replyParameters().getClass());
    }

    if (sendDocument.document() != null) {
      final var file = sendDocument.document();
      multiPartForm.binaryFileUpload(
          "document",
          file.getName(),
          file.getAbsolutePath(),
          MediaType.APPLICATION_OCTET_STREAM);
    }

    return client.sendDocument(multiPartForm);
  }

  public Uni<String> sendMessage(long chatId, String text) {

    final var sendMessage = SendMessage.builder()
        .chatId(chatId)
        .text(text)
        .build();

    return request(sendMessage);
  }

  public Completable rxSendMessage(long chatId, String text) {
    return RxUtil.single(sendMessage(chatId, text)).ignoreElement();
  }

  public Uni<String> sendMessage(SendMessage sendMessage) {
    return request(sendMessage);
  }

  public Uni<String> setWebhook(String url) {
    final var webHookRequest = WebHookRequest.builder()
        .url(URI.create(url))
        .build();
    return client.setWebhook(webHookRequest);
  }

  public Uni<String> deleteWebhook() {
    return client.deleteWebhook(false);
  }

  public Uni<String> getWebhookInfo() {
    return client.getWebhookInfo();
  }

  public Uni<String> me() {
    return client.getMe();
  }

  public Uni<List<TelegramUpdate>> getUpdates(GetUpdatesRequest getUpdatesRequest) {
    return client.getUpdates(getUpdatesRequest)
        .map(TelegramUpdateResponse::result);
  }

  public Uni<String> answerCallbackQuery(String callbackQueryId) {
    return client.answerCallbackQuery(AnswerCallbackQuery.builder()
            .callbackQueryId(callbackQueryId)
            .text(null)
            .showAlert(false)
            .cacheTime(0)
        .build());
  }

  public Uni<List<TelegramUpdate>> getUpdates() {
    return getUpdates(new GetUpdatesRequest(null, null, null, null));
  }

  public Uni<List<TelegramUpdate>> getUpdates(Long offset) {
    return getUpdates(GetUpdatesRequest.builder()
        .offset(offset)
        .timeout(RandomUtil.randomInt(10, 25))
        .build());
  }

  public Uni<String> editMessageText(EditMessageText editMessageText) {
    return client.editMessageText(editMessageText);
  }
}
