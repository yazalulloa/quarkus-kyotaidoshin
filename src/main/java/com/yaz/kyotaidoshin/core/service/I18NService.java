package com.yaz.kyotaidoshin.core.service;


import io.quarkiverse.renarde.util.I18N;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Named
@RequestScoped
public class I18NService {

  @Inject
  I18N i18N;

  public String month(int i) {
    return i18N.getMessage("months.number." + i);
  }

  public String getMessage(String str) {
    return i18N.getMessage(str);
  }

  public void setLanguage(String language) {
    if (language != null) {
      //log.info("Language change {} -> {}", i18N.getLanguage(), language);
      i18N.set(language);
    }
  }

  public void printLanguage() {
    log.info("Current language: {}", i18N.getLanguage());
  }

  public record MonthType(
      int month,
      String name
  ) {

  }

  public MonthType[] monthTypes() {
    final var types = new MonthType[12];
    for (int i = 0; i < 12; i++) {
      types[i] = new MonthType(i + 1, month(i + 1));
    }
    return types;
  }

  public String joinMonths(Set<Integer> months) {
    return months.stream()
        .map(this::month)
        .collect(Collectors.joining(", "));
  }

}
