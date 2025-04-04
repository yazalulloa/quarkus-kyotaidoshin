package com.yaz.kyotaidoshin.persistence.turso.ws.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@RequiredArgsConstructor
public final class StmtResult {

  @JsonProperty
  private final Col[] cols;
  @JsonProperty
  private final Value[][] rows;
  @JsonProperty
  private final long affectedRowCount;
  @JsonProperty
  private final String lastInsertRowid;


  public int rowCount() {
    return (int) affectedRowCount;
  }
}
