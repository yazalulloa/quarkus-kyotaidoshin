package com.yaz.kyotaidoshin.api.domain.response.rate;

import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public final class RateHistoricProgressUpdate {

  private String clientId;
  private String left;
//  private String right;
  private int counter;
  private int size;
  private boolean end;

  public String right() {
    if (counter == 0 && size == 0) {
      return null;
    }

    return "%s/%s".formatted(counter, size);
  }

}
