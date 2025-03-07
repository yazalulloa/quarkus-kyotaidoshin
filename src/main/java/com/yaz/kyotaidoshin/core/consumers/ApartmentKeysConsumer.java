package com.yaz.kyotaidoshin.core.consumers;

import com.yaz.kyotaidoshin.core.service.DebtService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.persistence.model.Apartment;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ApartmentKeysConsumer {

  private final DebtService debtService;
  private final ExtraChargeService extraChargeService;


  public void apartmentDeleted(@ObservesAsync Apartment.Keys keys) {
    log.debug("apartmentDeleted: {}", keys);
    Uni.join()
        .all(
            debtService.deleteByApartment(keys.buildingId(), keys.number()),
            extraChargeService.deleteByApartment(keys.buildingId(), keys.number()))
        .andCollectFailures()
        .subscribe()
        .with(
            i -> {
              log.debug("Deleting apartment data: {} {} deleted: {}", keys.buildingId(), keys.number(), i);
            },
            e -> {
              log.error("ERROR deleting apartment data: {} {}", keys.buildingId(), keys.number(), e);
            });
  }
}
