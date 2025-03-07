package com.yaz.kyotaidoshin.api.domain.response.apartments;

import lombok.Builder;

@Builder(toBuilder = true)
public record ApartmentCountersDto(long totalCount, Long queryCount) {

  public ApartmentCountersDto minusOne() {
    return new ApartmentCountersDto(totalCount - 1, queryCount == null ? null : queryCount - 1);
  }
}
