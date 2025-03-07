package com.yaz.kyotaidoshin.api.domain.response.receipt;

import java.util.Collection;
import lombok.Builder;

@Builder
public record ReceiptTableResponse(
    ReceiptCountersDto countersDto,
    String lastKey,
    Collection<ReceiptTableItem> results) {

}
