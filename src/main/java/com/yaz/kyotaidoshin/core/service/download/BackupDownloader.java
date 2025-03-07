package com.yaz.kyotaidoshin.core.service.download;

import com.yaz.kyotaidoshin.core.service.ApartmentService;
import com.yaz.kyotaidoshin.core.service.domain.FileResponse;
import com.yaz.kyotaidoshin.core.service.telegram.BackupOptions;
import com.yaz.kyotaidoshin.util.ZipUtility;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class BackupDownloader {

  private final Vertx vertx;
  private final BuildingDownloader buildingDownloader;
  private final ReceiptDownloader receiptDownloader;
  private final ApartmentService apartmentService;

  public Single<Path> all() {

    return Single.defer(() -> {

      final var dirPath = Paths.get("tmp", UUID.randomUUID().toString());

      final var mkdirs = vertx.fileSystem().rxMkdirs(dirPath.toString());

      final var backupSingle = Single.zip(buildingDownloader.downloadFile(), receiptDownloader.downloadFile(),
          apartmentService.downloadFile(),
          (buildings, receipts, apartments) -> {

            final var filePath = dirPath.resolve("backup.tar.gz");
            ZipUtility.createTarGzipFiles(filePath,
                List.of(buildings.path().toPath(), receipts.path().toPath(), apartments.path().toPath()));

            return filePath;
          });

      return mkdirs.andThen(backupSingle);
    });

  }

  public Single<Path> backup(BackupOptions backupOptions) {
    return switch (backupOptions) {
      case BUILDINGS -> buildingDownloader.downloadFile().map(FileResponse::path).map(File::toPath);
      case RECEIPTS -> receiptDownloader.downloadFile().map(FileResponse::path).map(File::toPath);
      case APARTMENTS -> apartmentService.downloadFile().map(FileResponse::path).map(File::toPath);
      default -> all();
    };
  }
}
