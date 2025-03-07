package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.util.EnvParams;
import com.yaz.kyotaidoshin.util.FileUtil;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import io.quarkus.scheduler.Scheduled;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.smallrye.mutiny.Uni;
import io.vertx.rxjava3.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DeleteTmpDirJob {

  private final Vertx vertx;
  private final EnvParams envParams;

  @Scheduled(every = "30m")
  Uni<Void> deleteDir() {
//    if (true) {
//      return Uni.createFrom().voidItem();
//    }

//    log.info("Deleting tmp dir");

    return deleteDirNow("tmp");
  }

  Uni<Void> deleteDirNow(String path) {

    final var showDir = !envParams.isShowDir() ? Completable.complete() :
        vertx.rxExecuteBlocking(FileUtil::showDir)
            .doOnSuccess(str -> log.info("\n{}", str))
            .ignoreElement();

    return MutinyUtil.toUni(showDir.andThen(deleteFile(path)));
  }

  private Completable deleteFile(String file) {
    return vertx.fileSystem().rxExists(file)
        .filter(b -> b)
        .flatMapSingle(ignored -> vertx.fileSystem().rxProps(file))
        .flatMapCompletable(fileProps -> {
          if (fileProps.isDirectory()) {

            return vertx.fileSystem().rxReadDir(file)
                .flatMapObservable(Observable::fromIterable)
                .flatMapCompletable(this::deleteFile)
                .andThen(vertx.fileSystem().rxReadDir(file))
                .filter(List::isEmpty)
                .filter(l -> !file.equals("tmp"))
                .flatMapCompletable(l -> vertx.fileSystem().rxDelete(file));
          }

          final var lastAccessTime = fileProps.lastAccessTime();
          final var diff = ChronoUnit.HOURS.between(Instant.ofEpochMilli(lastAccessTime), Instant.now());

          if (diff <= 1) {
            return Completable.complete();
          }

          return vertx.fileSystem().rxDeleteRecursive(file, true)
              .doOnError(t -> log.error("FAILED_TO_DELETE {}", file))
              .onErrorComplete();
        });

  }
}
