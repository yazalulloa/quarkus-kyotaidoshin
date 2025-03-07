package com.yaz.kyotaidoshin.api.domain.response.receipt;

import com.yaz.kyotaidoshin.api.domain.response.rate.RateTableResponse;
import com.yaz.kyotaidoshin.core.service.csv.CsvReceipt;
import java.util.List;
import lombok.Builder;

@Builder
public record ReceiptFileFormDto(
    int month,
    int year,
    int[] years,
    String buildingName,
    String fileName,
    CsvReceipt receipt,
    String date,
    List<String> buildings,
    RateTableResponse rates,
    String data
) {

}
