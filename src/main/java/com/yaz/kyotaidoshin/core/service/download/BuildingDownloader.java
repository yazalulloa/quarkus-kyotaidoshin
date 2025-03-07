package com.yaz.kyotaidoshin.core.service.download;

import com.yaz.kyotaidoshin.core.service.BuildingService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.core.service.ReserveFundService;
import com.yaz.kyotaidoshin.core.service.domain.BuildingRecord;
import com.yaz.kyotaidoshin.core.service.domain.FileResponse;
import com.yaz.kyotaidoshin.persistence.domain.BuildingQuery;
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
public class BuildingDownloader implements ListService<BuildingRecord, BuildingQuery> {

  private final WriteEntityToFile writeEntityToFile;
  private final BuildingService buildingService;
  private final ReserveFundService reserveFundService;
  private final ExtraChargeService extraChargeService;

  public PagingProcessor<List<BuildingRecord>> pagingProcessor(int pageSize, SortOrder sortOrder) {
    return new ListServicePagingProcessorImpl<>(this,
        BuildingQuery.builder().limit(pageSize).sortOrder(sortOrder).build());
  }

  public Single<FileResponse> downloadFile() {
    return writeEntityToFile.downloadFile("buildings.json.gz", pagingProcessor(100, SortOrder.ASC));
  }

  @Override
  public Single<List<BuildingRecord>> listByQuery(BuildingQuery query) {
    return MutinyUtil.single(buildingService.list(query))
        .flatMapObservable(Observable::fromIterable)
        .flatMapSingle(building -> {

          final var reserveFundSingle = MutinyUtil.single(reserveFundService.listByBuilding(building.id()));
          final var extraChargeSingle = MutinyUtil.single(extraChargeService.by(building.id(), building.id()));

          return Single.zip(reserveFundSingle, extraChargeSingle, (reserveFund, extraCharge) -> {
            return BuildingRecord.builder()
                .building(building)
                .reserveFunds(reserveFund)
                .extraCharges(extraCharge)
                .build();
          });

        })
        .toList();
  }

  @Override
  public BuildingQuery nextQuery(List<BuildingRecord> list, BuildingQuery query) {
    if (list.isEmpty()) {
      return query;
    }

    return query.toBuilder()
        .lastId(list.getLast().building().id())
        .build();
  }
}
