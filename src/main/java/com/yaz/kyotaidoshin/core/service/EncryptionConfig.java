package com.yaz.kyotaidoshin.core.service;

import io.smallrye.config.ConfigMapping;
import jakarta.validation.constraints.NotBlank;

@ConfigMapping(prefix = "app.encryption")
public interface EncryptionConfig {

  @NotBlank
  String secretKey();

  @NotBlank
  String separator();

  @NotBlank
  String algorithm();

  @NotBlank
  String transformation();

  int ivSize();

  int parameterSpecLen();

}
