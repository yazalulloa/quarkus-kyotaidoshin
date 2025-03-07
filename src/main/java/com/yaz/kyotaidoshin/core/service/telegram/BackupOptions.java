package com.yaz.kyotaidoshin.core.service.telegram;

public enum BackupOptions {
  APARTMENTS, BUILDINGS, RECEIPTS, ALL;


  public static final BackupOptions[] OPTIONS = BackupOptions.values();

  public String withPrefix() {
    return "BACKUP_" + name();
  }


  public static BackupOptions option(String option) {
    for (BackupOptions backupOption : OPTIONS) {
      if (backupOption.withPrefix().equals(option)) {
        return backupOption;
      }
    }
    return null;
  }
}
