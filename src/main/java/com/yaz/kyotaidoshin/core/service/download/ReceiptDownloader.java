package com.yaz.kyotaidoshin.core.service.download;

import com.yaz.kyotaidoshin.core.service.DebtService;
import com.yaz.kyotaidoshin.core.service.ExpenseService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.core.service.ReceiptService;
import com.yaz.kyotaidoshin.core.service.domain.FileResponse;
import com.yaz.kyotaidoshin.core.service.domain.ReceiptRecord;
import com.yaz.kyotaidoshin.persistence.domain.ReceiptQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.util.ListService;
import com.yaz.kyotaidoshin.util.ListServicePagingProcessorImpl;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingProcessor;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ReceiptDownloader implements ListService<ReceiptRecord, ReceiptQuery> {

  private final WriteEntityToFile writeEntityToFile;
  private final ReceiptService receiptService;
  private final ExpenseService expenseService;
  private final ExtraChargeService extraChargeService;
  private final DebtService debtService;

  public PagingProcessor<List<ReceiptRecord>> pagingProcessor(int pageSize, SortOrder sortOrder) {
    return new ListServicePagingProcessorImpl<>(this,
        ReceiptQuery.builder().limit(pageSize).build());
  }

  public Single<FileResponse> downloadFile() {
    return writeEntityToFile.downloadFile("receipts.json.gz", pagingProcessor(100, SortOrder.ASC));
  }

  @Override
  public Single<List<ReceiptRecord>> listByQuery(ReceiptQuery query) {
    return MutinyUtil.single(receiptService.select(query))
        .flatMapObservable(Observable::fromIterable)
        .flatMapSingle(receipt -> {

          final var expenseSingle = MutinyUtil.single(expenseService.readByReceipt(receipt.id()));
          final var debtSingle = MutinyUtil.single(debtService.readByReceipt(receipt.buildingId(), receipt.id()));

          final var extraChargeSingle = MutinyUtil.single(
              extraChargeService.by(receipt.buildingId(), String.valueOf(receipt.id())));

          return Single.zip(expenseSingle, debtSingle, extraChargeSingle, (expenses, debts, extraCharge) -> {
            return ReceiptRecord.builder()
                .receipt(receipt)
                .extraCharges(extraCharge)
                .expenses(expenses)
                .debts(debts)
                .build();
          });

        })
        .toList();
  }

  @Override
  public ReceiptQuery nextQuery(List<ReceiptRecord> list, ReceiptQuery query) {
    if (list.isEmpty()) {
      return query;
    }

    return query.toBuilder()
        .lastId(list.getLast().receipt().id())
        .build();
  }
}
