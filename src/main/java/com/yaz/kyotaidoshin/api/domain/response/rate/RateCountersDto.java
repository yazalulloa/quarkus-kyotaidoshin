package com.yaz.kyotaidoshin.api.domain.response.rate;

import lombok.Builder;

@Builder
public record RateCountersDto(
    Long queryCount,
    long totalCount
) {

}
