package com.yaz.kyotaidoshin.api.domain.response.funds;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReserveFundInitFormDto(
    String key,
    List<ReserveFundTableItem> reserveFunds
) {

}
