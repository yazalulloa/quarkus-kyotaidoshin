package com.yaz.kyotaidoshin.api.domain.response.apartments;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@Builder(toBuilder = true)
public class ApartmentFormDto {


  private final String key;
  private final String generalFieldError;
  private final List<String> buildings;
  private final String buildingId;
  private final String buildingIdFieldError;
  private final boolean isEdit;
  private final boolean readOnlyBuilding;
  private final boolean showForm;
  private final String number;
  private final String numberFieldError;
  private final boolean readOnlyNumber;
  private final String name;
  private final String nameFieldError;
  private final BigDecimal aliquot;
  private final String aliquotFieldError;
  private final List<EmailForm> emails;
  private final boolean hideForm;
  private final ApartmentTableResponse.Item item;
  private final boolean isNew;

  public boolean isSuccess() {
    return generalFieldError == null
        && buildingIdFieldError == null
        && numberFieldError == null
        && nameFieldError == null
        && aliquotFieldError == null
        && (emails == null || emails.isEmpty() ||
        emails.stream().allMatch(emailForm -> emailForm.error == null));
  }

  public record EmailForm(
      String value,
      String error
  ) {

    public static EmailForm ofValue(String value) {
      return new EmailForm(value, null);
    }

  }
}
