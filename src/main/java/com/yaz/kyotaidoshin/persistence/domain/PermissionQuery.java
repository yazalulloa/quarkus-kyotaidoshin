package com.yaz.kyotaidoshin.persistence.domain;

import com.yaz.kyotaidoshin.persistence.model.Permission;
import java.util.Set;
import lombok.Builder;

@Builder(toBuilder = true)
public record PermissionQuery(
    Permission.Keys lastKeys,
    Set<String> userIds,
    Set<String> types,
    SortOrder sortOrder,
    int limit
) {

}
