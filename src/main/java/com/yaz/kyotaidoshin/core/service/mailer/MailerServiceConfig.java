package com.yaz.kyotaidoshin.core.service.mailer;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Set;

@ConfigMapping(prefix = "app.mail")
public interface MailerServiceConfig {

  String configs();

  Set<String> receiptTo();

  @WithDefault("false")
  boolean useAlternativeReceiptTo();

}
