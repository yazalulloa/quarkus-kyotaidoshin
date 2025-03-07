package com.yaz.kyotaidoshin.core.consumers;

import com.yaz.kyotaidoshin.core.domain.events.BuildingDeleted;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.core.service.ReserveFundService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class BuildingDeletedConsumer {

  private final ExtraChargeService extraChargeService;
  private final ReserveFundService reserveFundService;


  public void buildingDeleted(@ObservesAsync BuildingDeleted task) {
    log.debug("buildingDeleted: {}", task);

    Uni.combine()
        .all()
        .unis(reserveFundService.deleteByBuilding(task.id()),
            extraChargeService.deleteByBuilding(task.id()))
        .with(Integer::sum)
        .subscribe()
        .with(
            i -> {
              log.debug("Deleting extra charges: {} deleted: {}", task.id(), i);
            },
            e -> {
              log.error("ERROR deleting extra charge BuildingDeleted: {}", task.id(), e);
            });
  }

}
