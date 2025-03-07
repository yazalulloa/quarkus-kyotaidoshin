package com.yaz.kyotaidoshin.core.client.telegram;

import jakarta.ws.rs.core.MediaType;
import java.io.File;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;


@Data
@Builder
@Accessors(fluent = true)
public class SendDocument {

  @RestForm("chat_id")
  long chatId;
  @RestForm
  String caption;
  @RestForm
  ParseMode parseMode;
  @RestForm
  File document;
  @RestForm("reply_parameters")
  @PartType(MediaType.APPLICATION_JSON)
  ReplyParameters replyParameters;

}
