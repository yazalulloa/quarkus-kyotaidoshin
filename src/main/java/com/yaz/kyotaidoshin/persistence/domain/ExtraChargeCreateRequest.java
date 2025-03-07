package com.yaz.kyotaidoshin.persistence.domain;

import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import java.util.Collection;
import lombok.Builder;

@Builder
public record ExtraChargeCreateRequest(
    String parentReference,
    String buildingId,
    ExtraCharge.Type type,
    String description,
    double amount,
    String currency,
    boolean active,
    Collection<String> apartments) {

}
