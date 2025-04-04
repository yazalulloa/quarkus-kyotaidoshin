package com.yaz.kyotaidoshin.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Collections;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder(toBuilder = true)
@Accessors(fluent = true)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApartmentQuery {

  private final String lastBuildingId;
  private final String lastNumber;
  private final String q;
  @Builder.Default
  private final Set<String> buildings = Collections.emptySet();
  @Builder.Default
  private final int limit = 30;

  public boolean hasQuery() {
    return q != null && !q.isBlank() && buildings != null && !buildings.isEmpty();
  }
}
