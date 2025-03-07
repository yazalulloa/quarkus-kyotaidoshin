package com.yaz.kyotaidoshin.core.service.mailer;

import lombok.Builder;

@Builder
public record MailerConfig(
    String key,
    String authMethods,
    String host,
    int port,
    String username,
    String password
) {

}
