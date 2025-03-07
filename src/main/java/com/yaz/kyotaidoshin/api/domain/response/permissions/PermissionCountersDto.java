package com.yaz.kyotaidoshin.api.domain.response.permissions;

import lombok.Builder;

@Builder(toBuilder = true)
public record PermissionCountersDto(
    long totalCount,
    Long queryCount
) {

}
