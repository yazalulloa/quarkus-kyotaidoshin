package com.yaz.kyotaidoshin.core.bean;

import com.yaz.kyotaidoshin.util.VertxUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.file.OpenOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.file.FileSystem;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class VertxHelper {

  private final Vertx vertx;

  public FileSystem fileSystem() {
    return vertx.fileSystem();
  }

  public Completable writeFile(String filePath, byte[] file) {

    return fileSystem().open(filePath, new OpenOptions().setWrite(true).setSync(true).setDsync(true))
        .flatMapCompletable(asyncFile -> asyncFile.write(Buffer.buffer(file))
            .andThen(asyncFile.flush())
            .andThen(asyncFile.close()));
  }

  public Single<Long> crc32FullLoad(String path) {
    return fileSystem().readFile(path)
        .map(Buffer::getBytes)
        .map(bytes -> {
          final var crc32 = new CRC32();
          crc32.update(bytes);
          return crc32.getValue();
        });
  }

  public Single<FileWithHash> fileWithHash(String path) {
    return fileSystem().rxReadFile(path)
        .map(buffer -> {
          final var crc32 = new CRC32();
          crc32.update(buffer.getBytes());
          return new FileWithHash(buffer, buffer.length(), crc32.getValue());
        });
  }

  public <T> Single<T> single(Consumer<Handler<AsyncResult<T>>> consumer) {
    final var source = VertxUtil.singleOnSubscribe(consumer);
    return Single.create(source);
  }

  public Single<Long> crc32(String path) {

    return fileSystem().rxOpen(path, new OpenOptions().setRead(true))
        .flatMap(asyncFile -> {
          return Single.<Long>create(emitter -> {

            final var crc32 = new CRC32();

            asyncFile.handler(b -> {
                  final var bytes = b.getBytes();
                  crc32.update(bytes);
                })
                .endHandler(v -> {
                  asyncFile.close()
                      .subscribe(() -> emitter.onSuccess(crc32.getValue()), emitter::onError);

                })
                .exceptionHandler(t -> {
                  asyncFile.close()
                      .subscribe(() -> {
                      }, tc -> {
                      });
                  emitter.onError(t);
                });
          });


        })
        .doOnError(throwable -> log.error("Error while hashing", throwable));
  }

  public record FileWithHash(Buffer buffer, long fileSize, long crc32) {

  }
}
