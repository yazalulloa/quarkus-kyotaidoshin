package com.yaz.kyotaidoshin.util;

import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SqlUtil {

  public static final DateTimeFormatter SQLITE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static final String AND = " AND ";

  private SqlUtil() {
  }

  public static String params(int size) {
    return Stream.generate(() -> "?").limit(size)
        .collect(Collectors.joining(","));
  }

  public static String valuesParams(int paramsSize, int valuesSize) {
    return Stream.generate(() -> "(" + params(paramsSize) + ")")
        .limit(valuesSize)
        .collect(Collectors.joining(","));
  }

  public static String escape(Object object) {
    return Optional.ofNullable(object)
        .map(Object::toString)
        .map(s -> s.replace("'", "''").replace("\\", "\\\\"))
        .map(s -> "'" + s + "'")
        .orElse("null");
  }

  public static String formatDateSqlite(TemporalAccessor temporalAccessor) {
    if (temporalAccessor == null) {
      return null;
    }
    return SQLITE_DATE_TIME_FORMATTER.format(temporalAccessor);
  }

  public static Integer rowCount(ExecuteResp... resps) {
    int affected = 0;
    for (var resp : resps) {
      affected += resp.result().rowCount();
    }
    return affected;
  }
}
