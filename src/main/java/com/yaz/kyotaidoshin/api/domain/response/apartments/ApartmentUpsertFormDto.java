package com.yaz.kyotaidoshin.api.domain.response.apartments;

import lombok.Builder;

@Builder(toBuilder = true)
public record ApartmentUpsertFormDto(
    String buildingError,
    String numberError,
    String nameError,
    String aliquotError,
    String generalError,
    ApartmentTableResponse.Item item
) {

  public boolean isSuccess() {
    return generalError == null
        && buildingError == null
        && numberError == null
        && nameError == null
        && aliquotError == null;
  }
}
