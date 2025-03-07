package com.yaz.kyotaidoshin.core.consumers;

import com.yaz.kyotaidoshin.core.service.DebtService;
import com.yaz.kyotaidoshin.core.service.ExpenseService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ReceiptKeysConsumer {
  private final ExtraChargeService extraChargeService;
  private final DebtService debtService;
  private final ExpenseService expenseService;

  public void receiptDeleted(@ObservesAsync Receipt.Keys keys) {
    log.debug("receiptDeleted: {}", keys);
    Uni.join()
        .all(extraChargeService.deleteByReceipt(keys.buildingId(), String.valueOf(keys.id())),
            debtService.deleteByReceipt(keys.buildingId(), keys.id()),
            expenseService.deleteByReceipt(keys.buildingId(), keys.id()))
        .andCollectFailures()
        .subscribe()
        .with(
            i -> log.debug("Deleting receipt data: {} {} deleted: {}", keys.buildingId(), keys.id(), i),
            e -> log.error("ERROR deleting receipt data: {} {}", keys.buildingId(), keys.id(), e));

  }

}
