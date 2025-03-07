package com.yaz.kyotaidoshin.core.service.telegram;

public enum BotOptions {

  LOGS,
  RECEIPTS,
  LAST_RATE,
  BACKUPS,
  SYSTEM_INFO;

  public static final BotOptions[] OPTIONS = values();

  public static BotOptions option(String option) {
    for (BotOptions botOption : OPTIONS) {
      if (botOption.name().equals(option)) {
        return botOption;
      }
    }
    return null;
  }
}